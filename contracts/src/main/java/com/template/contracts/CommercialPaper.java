package com.template.contracts;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.*;
import net.corda.core.crypto.NullKeys;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.node.ServiceHub;
import net.corda.core.transactions.LedgerTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.finance.workflows.asset.CashUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.persistence.criteria.CriteriaBuilder;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;
import static net.corda.finance.contracts.CommercialPaperKt.CP_PROGRAM_ID;
import static net.corda.finance.contracts.utils.StateSumming.sumCashBy;

public class CommercialPaper implements Contract {

    public static final String CP_CONTRACT_ID = "com.template.contracts.CommercialPaper";

    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {
        List<LedgerTransaction.InOutGroup<State, State>> groups = tx.groupStates(State.class, State::withoutOwner);
        CommandWithParties<Commands> cmd = requireSingleCommand(tx.getCommands(), Commands.class);
        TimeWindow timeWindow = tx.getTimeWindow();

        for (LedgerTransaction.InOutGroup group : groups) {
            List<State> inputs = group.getInputs();
            List<State> outputs = group.getOutputs();

            if (cmd.getValue() instanceof Commands.Move) {
                State input = inputs.get(0);

                requireThat(require -> {
                    require.using("the transaction is signed by the owner of the CP", cmd.getSigners().contains(input.getOwner().getOwningKey()));
                    require.using("the state is propagated", outputs.size() == 1);
                    // Don't need to check anything else, if outputs.size == 1 then the output is equal to the input (ignoring the owner field) due to the grouping.
                    return null;
                });
            }
            else if (cmd.getValue() instanceof Commands.Redeem) {
                // Redemption of the paper requires the movement of on-ledger cash.
                State input = inputs.get(0);
                Amount<Issued<Currency>> received = sumCashBy(tx.getOutputStates(), input.getOwner());

                if (timeWindow == null) {
                    throw new IllegalArgumentException("redemptions must be timestamped");
                }

                Instant time = timeWindow.getFromTime();

                requireThat(require -> {
                    assert time != null;
                    require.using("the paper must have matured", time.isAfter(input.getMaturityDate()));
                    require.using("the received amount equals the face value", received == input.getFaceValue());
                    require.using("the paper must be destroyed", outputs.isEmpty());
                    require.using("the transaction is signed by the owner of the CP", cmd.getSigners().contains(input.getOwner().getOwningKey()));
                    return null;
                });
            }
            else if (cmd.getValue() instanceof Commands.Issue) {
                State output = outputs.get(0);

                if (timeWindow == null) {
                    throw new IllegalArgumentException("issuances must have a time window");
                }

                Instant time = timeWindow.getUntilTime();

                requireThat(require -> {
                    // Don't allow people to issue commercial paper under other entities' identities.
                    require.using("output states are issued by a command signer", cmd.getSigners().contains(output.getIssuance().getParty().getOwningKey()));
                    require.using("output values sum to more than the inputs", output.getFaceValue().getQuantity() > 0);
                    assert time != null;
                    require.using("the maturity date is not in the past", time.isBefore(output.getMaturityDate()));
                    // Don't allow an existing CP state to be replaced by this issuance.
                    require.using("can't reissue an existing state", inputs.isEmpty());
                    return null;
                });
            }
            else {
                throw new IllegalArgumentException("unrecognised command");
            }
        }
        // throw new UnsupportedOperationException();
    }

    public TransactionBuilder generateIssue(PartyAndReference issuance, Amount<Issued<Currency>> faceValue, Instant maturityDate, Party notary) {
        State state = new State(issuance, issuance.getParty(), faceValue, maturityDate);
        StateAndContract stateAndContract = new StateAndContract(state, CP_PROGRAM_ID);
        return new TransactionBuilder(notary).withItems(stateAndContract, new Command<CommandData>(new Commands.Issue(), issuance.getParty().getOwningKey()));
    }

    public void generateMove(TransactionBuilder tx, StateAndRef<State> paper, AbstractParty newOwner) {
        tx.addInputState(paper);
        OwnableState outputState = paper.getState().getData().withNewOwner(newOwner).getOwnableState();
        tx.addOutputState(outputState, CP_PROGRAM_ID);
        tx.addCommand(new Command<CommandData>(new Commands.Move(), paper.getState().getData().getOwner().getOwningKey()));
    }

    public void generateRedeem(TransactionBuilder tx, StateAndRef<State> paper, ServiceHub services) throws InsufficientBalanceException {
        // Add the cash movement using the states in out vault.
        CashUtils.generateSpend(
                services,
                tx,
                new Amount<>(
                        paper.getState().getData().getFaceValue().getQuantity(),
                        paper.getState().getData().getFaceValue().getDisplayTokenSize(),
                        paper.getState().getData().getFaceValue().getToken().getProduct()
                ),
                services.getMyInfo().getLegalIdentities().get(0),
                (Set<AbstractParty>) paper.getState().getData().getOwner()
        );

        tx.addInputState(paper);
        tx.addCommand(new Command<CommandData>(new Commands.Redeem(), paper.getState().getData().getOwner().getOwningKey()));
    }

    public class State implements OwnableState {

        private PartyAndReference issuance;
        private AbstractParty owner;
        private Amount<Issued<Currency>> faceValue;
        private Instant maturityDate;

        public State() {
        }   // for serialisation

        public State(PartyAndReference issuance, AbstractParty owner, Amount<Issued<Currency>> faceValue, Instant maturityDate) {
            this.issuance = issuance;
            this.owner = owner;
            this.faceValue = faceValue;
            this.maturityDate = maturityDate;
        }

        public State copy() {
            return new State(this.issuance, this.owner, this.faceValue, this.maturityDate);
        }

        public State withoutOwner() {
            return new State(this.issuance, new AnonymousParty(NullKeys.NullPublicKey.INSTANCE), this.faceValue, this.maturityDate);
        }

        @NotNull
        @Override
        public AbstractParty getOwner() {
            return this.owner;
        }

        @NotNull
        @Override
        public CommandAndState withNewOwner(@NotNull AbstractParty newOwner) {
            return new CommandAndState(new CommercialPaper.Commands.Move(), new State(this.issuance, newOwner, this.faceValue, this.maturityDate));
        }

        public PartyAndReference getIssuance() {
            return this.issuance;
        }

        public Amount<Issued<Currency>> getFaceValue() {
            return this.faceValue;
        }

        public Instant getMaturityDate() {
            return this.maturityDate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            State state = (State) o;

            if (!Objects.equals(this.issuance, state.issuance)) {
                return false;
            }

            if (!Objects.equals(this.owner, state.owner)) {
                return false;
            }

            if (!Objects.equals(this.faceValue, state.faceValue)) {
                return false;
            }

            return Objects.equals(this.maturityDate, state.maturityDate);
        }

        @Override
        public int hashCode() {
            int result = this.issuance != null ? this.issuance.hashCode() : 0;
            result = 31 * result + (this.owner != null ? this.owner.hashCode() : 0);
            result = 31 * result + (this.faceValue != null ? this.faceValue.hashCode() : 0);
            result = 31 * result + (this.maturityDate != null ? this.maturityDate.hashCode() : 0);
            return result;
        }

        @NotNull
        @Override
        public List<AbstractParty> getParticipants() {
            return ImmutableList.of(this.owner);
        }
    }

    public static class Commands implements CommandData {
        public static class Move extends Commands {
            @Override
            public boolean equals(Object o) {
                return o instanceof Move;
            }
        }

        public static class Redeem extends Commands {
            @Override
            public boolean equals(Object o) {
                return o instanceof Redeem;
            }
        }

        public static class Issue extends Commands {
            @Override
            public boolean equals(Object o) {
                return o instanceof Issue;
            }
        }
    }
}
