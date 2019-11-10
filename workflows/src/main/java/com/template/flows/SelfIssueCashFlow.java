package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.contracts.Amount;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.transactions.CoreTransaction;
import net.corda.core.transactions.LedgerTransaction;
import net.corda.core.utilities.OpaqueBytes;
import net.corda.core.utilities.ProgressTracker;
import net.corda.finance.contracts.asset.Cash;
import net.corda.finance.flows.AbstractCashFlow;
import net.corda.finance.flows.CashIssueFlow;

import java.security.SignatureException;
import java.util.Currency;

// ******************
// * Initiator flow *
// ******************
@InitiatingFlow
@StartableByRPC
public class SelfIssueCashFlow extends FlowLogic<Cash.State> {

    private final ProgressTracker progressTracker = new ProgressTracker();
    private Amount<Currency> amount;

    public SelfIssueCashFlow(Amount<Currency> amount) {
        this.amount = amount;
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @Suspendable
    @Override
    public Cash.State call() throws FlowException {

        this.progressTracker.setCurrentStep(new ProgressTracker.Step("Gathering the required inputs."));
        OpaqueBytes issueRef = OpaqueBytes.of((byte) 0);
        Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

        this.progressTracker.setCurrentStep(new ProgressTracker.Step("Issuing cash."));
        AbstractCashFlow.Result cashIssueSubflowResult = subFlow(new CashIssueFlow(this.amount, issueRef, notary));

        this.progressTracker.setCurrentStep((new ProgressTracker.Step("Returning the newly issued cash state.")));

        LedgerTransaction cashIssueTx = null;
        try {
            cashIssueTx = cashIssueSubflowResult.getStx().toLedgerTransaction(getServiceHub());
        } catch (SignatureException e) {
            e.printStackTrace();
        }

        return cashIssueTx.outputsOfType(Cash.State.class).get(0);
    }
}
