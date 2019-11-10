package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.template.contracts.DummyContract;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.ContractState;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static com.template.contracts.DummyContract.Commands.Issue;
import static net.corda.core.contracts.ContractsDSL.requireThat;


public class DummyContractIssueFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class InitiatorFlow extends FlowLogic<SignedTransaction> {

        private final DummyContract.State state;
        private final ProgressTracker progressTracker = new ProgressTracker();

        public InitiatorFlow(DummyContract.State state) {
            this.state = state;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {

            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            final Command<Issue> issueCommand = new Command<Issue>(new Issue(), this.state.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList()));

            final TransactionBuilder txBuilder = new TransactionBuilder(notary);
            txBuilder.addOutputState(this.state, DummyContract.DUMMY_CONTRACT_ID);
            txBuilder.setTimeWindow(Instant.now(), Duration.ofDays(2));
            txBuilder.addCommand(issueCommand);
            txBuilder.verify(getServiceHub());
            final SignedTransaction ptx = getServiceHub().signInitialTransaction(txBuilder);

            List<Party> otherParties = this.state.getParticipants().stream().map(el -> (Party) el).collect(Collectors.toList());
            otherParties.remove(getOurIdentity());

            List<FlowSession> sessions = otherParties.stream().map(el -> initiateFlow(el)).collect(Collectors.toList());
            SignedTransaction stx = subFlow(new CollectSignaturesFlow(ptx, sessions));

            return subFlow(new FinalityFlow(stx, sessions));
        }
    }

    @InitiatedBy(DummyContractIssueFlow.InitiatorFlow.class)
    public static class ResponderFlow extends FlowLogic<SignedTransaction> {

        private final FlowSession flowSession;
        private SecureHash txWeJustSigned;
        private final ProgressTracker progressTracker = new ProgressTracker();

        public ResponderFlow(FlowSession flowSession) {
            this.flowSession = flowSession;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {

            class SignTxFlow extends SignTransactionFlow {

                private SignTxFlow(FlowSession flowSession, ProgressTracker progressTracker) {
                    super(flowSession, progressTracker);
                }

                @Override
                protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                    requireThat(req -> {
                        ContractState output = stx.getTx().getOutputs().get(0).getData();
                        req.using("This must be a dummy contract transaction.", output instanceof DummyContract.State);
                        return null;
                    });

                    txWeJustSigned = stx.getId();
                }
            }

            this.flowSession.getCounterpartyFlowInfo().getFlowVersion();
            SignTxFlow signTxFlow = new SignTxFlow(flowSession, SignTransactionFlow.Companion.tracker());
            subFlow(signTxFlow);
            return subFlow(new ReceiveFinalityFlow(flowSession, txWeJustSigned));
        }
    }

}
