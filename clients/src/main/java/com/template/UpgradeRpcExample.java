package com.template;

import com.template.contracts.DummyContract;
import net.corda.client.rpc.CordaRPCClient;
import net.corda.client.rpc.CordaRPCConnection;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.utilities.NetworkHostAndPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class UpgradeRpcExample {
    private static final Logger logger = LoggerFactory.getLogger(UpgradeRpcExample.class);

    public static void main(String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: TemplateClient <node address> <username> <password>");
        }
        final NetworkHostAndPort nodeAddress = NetworkHostAndPort.parse(args[0]);
        String username = args[1];
        String password = args[2];

        final CordaRPCClient client = new CordaRPCClient(nodeAddress);
        final CordaRPCConnection connection = client.start(username, password);
        final CordaRPCOps proxy = connection.getProxy();

        // check proxy
        logger.info(proxy.currentNodeTime().toString());

        // Party A issues DummyContract (basically a commercial paper).
        // DummyContract.State

        // proxy.startFlowDynamic()

        // Party B buys the DummyContract

        // Party A upgrades the DummyContract.

        connection.notifyServerAndClose();
    }
}