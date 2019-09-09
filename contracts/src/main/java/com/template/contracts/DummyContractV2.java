package com.template.contracts;

import net.corda.core.contracts.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DummyContractV2 implements UpgradedContractWithLegacyConstraint<DummyContract.State, DummyContractV2.State> {
    @NotNull
    @Override
    public AttachmentConstraint getLegacyContractConstraint() {
        return AlwaysAcceptAttachmentConstraint.INSTANCE;
    }

    @NotNull
    @Override
    public String getLegacyContract() {
        return DummyContract.class.getName();
    }

    @NotNull
    @Override
    public DummyContractV2.State upgrade(@NotNull DummyContract.State state) {
        return new DummyContractV2.State(state.getMagicNumber(), state.getParticipants());
    }

    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {
        // other verifications
    }

    public static class State implements ContractState {

        private int magicNumber = 0;
        private List<AbstractParty> owners;

        public State(int magicNumber, List<AbstractParty> owners) {
            this.magicNumber = magicNumber;
            this.owners = owners;
        }

        @NotNull
        @Override
        public List<AbstractParty> getParticipants() {
            return this.owners;
        }
    }

    public static class Commands implements CommandData {

        public static class Create extends Commands {
            @Override
            public boolean equals(Object o) {
                return o instanceof Create;
            }
        }

        public static class Move extends Commands {
            @Override
            public boolean equals(Object o) {
                return o instanceof Move;
            }
        }
    }
}
