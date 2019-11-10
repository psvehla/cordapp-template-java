package com.template;

import com.template.contracts.DummyContract;
import com.template.flows.DummyContractIssueFlow;
import net.corda.client.rpc.CordaRPCClient;
import net.corda.client.rpc.CordaRPCConnection;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.Issued;
import net.corda.core.contracts.PartyAndReference;
import net.corda.core.identity.AbstractParty;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowProgressHandle;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.NetworkHostAndPort;
import net.corda.core.utilities.OpaqueBytes;
import net.corda.finance.contracts.asset.Cash;
import net.corda.finance.flows.AbstractCashFlow;
import net.corda.finance.flows.CashIssueFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static net.corda.finance.Currencies.DOLLARS;
import static net.corda.finance.Currencies.issuedBy;
import static net.corda.finance.workflows.GetBalances.getCashBalances;

class UpgradeRpcExample {
    private static final Logger logger = LoggerFactory.getLogger(UpgradeRpcExample.class);

    public static void main(String[] args) {

        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: TemplateClient <node A address> <node B address> <username> <password>");
        }

        final NetworkHostAndPort nodeAAddress = NetworkHostAndPort.parse(args[0]);
        final NetworkHostAndPort nodeBAddress = NetworkHostAndPort.parse(args[1]);
        String username = args[2];
        String password = args[3];

        final CordaRPCClient clientA = new CordaRPCClient(nodeAAddress);
        final CordaRPCConnection connectionA = clientA.start(username, password);
        final CordaRPCOps proxyA = connectionA.getProxy();

        final CordaRPCClient clientB = new CordaRPCClient(nodeBAddress);
        final CordaRPCConnection connectionB = clientB.start(username, password);
        final CordaRPCOps proxyB = connectionB.getProxy();

        // check proxy
        logger.info(proxyA.currentNodeTime().toString());

        // Party A issues DummyContract (basically a commercial paper).
        logger.info("Ledger before: " + proxyA.vaultQuery(DummyContract.State.class).getStates());

        try {
            PartyAndReference issuance = new PartyAndReference((AbstractParty) proxyA.nodeInfo().getLegalIdentities().get(0), OpaqueBytes.of((byte) 0));
            AbstractParty owner = proxyA.nodeInfo().getLegalIdentities().get(0);
            Amount<Issued<Currency>> faceValue = Amount.fromDecimal(new BigDecimal(1000), new Issued<Currency> (issuance, Currency.getInstance(Locale.US)));
            Instant maturityDate = Instant.now().plus(7, ChronoUnit.DAYS);
            DummyContract.State state = new DummyContract.State(issuance, owner, faceValue, maturityDate);

            SignedTransaction result = proxyA.startTrackedFlowDynamic(DummyContractIssueFlow.InitiatorFlow.class, state).getReturnValue().get();
            logger.info("Transaction id " + result.getId() + " committed to ledger.\n" + result.getTx().getOutput(0));
            logger.info("Ledger after: " + proxyA.vaultQuery(DummyContract.State.class).getStates());
        } catch (InterruptedException e) {
            e.printStackTrace();
            logger.error(e.getMessage(), e);
        } catch (ExecutionException e) {
            e.printStackTrace();
            logger.error(e.getMessage(), e);
        }

        // Get party B some cash.
        Map<Currency, Amount<Currency>> cashBalances =  getCashBalances(proxyB);
        logger.info("cash balances");

        for (Currency currency : cashBalances.keySet()) {
            logger.info(cashBalances.get(currency).toString() + currency.getCurrencyCode());
        }

        Cash.State cashState = new Cash.State(issuedBy(DOLLARS(100), proxyB.nodeInfo().getLegalIdentities().get(0).ref((byte) 1, (byte) 1)), proxyB.nodeInfo().getLegalIdentities().get(0));

        try (FlowProgressHandle<AbstractCashFlow.Result> resultFlowProgressHandle = proxyB.startTrackedFlowDynamic(CashIssueFlow.class,
                                                                                                                   cashState,
                                                                                                                   proxyB.nodeInfo().getLegalIdentities().get(0).ref((byte) 1, (byte) 1),
                                                                                                                   proxyB.notaryIdentities().get(0))) {
        }

        // Party B buys the DummyContract.

        // Party A upgrades the DummyContract.

        connectionA.notifyServerAndClose();
        connectionB.notifyServerAndClose();
    }
}