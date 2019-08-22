package com.template.contracts;

import com.template.states.IOUState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.Party;
import net.corda.core.transactions.LedgerTransaction;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;

// ************
// * Contract *
// ************
public class IOUContract implements Contract {
    // This is used to identify our contract when building a transaction.
    public static final String ID = "com.template.contracts.IOUContract";

    // Our Create command.
    public static class Create implements CommandData {
    }

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    @Override
    public void verify(LedgerTransaction tx) {
        final CommandWithParties<IOUContract.Create> command = requireSingleCommand(tx.getCommands(), IOUContract.Create.class);

        // Constraints on the shape of the transaction.
        if (!tx.getInputs().isEmpty()) {
            throw new IllegalArgumentException("No inputs may be consumed when issuing and IOU.");
        }

        if (!((tx.getOutputs().size() == 1) && (tx.getOutput(0).getClass() == IOUState.class))) {
            throw new IllegalArgumentException("There must be one output state of type IOUState.");
        }

        // IOU specific constraints.
        final IOUState output = tx.outputsOfType(IOUState.class).get(0);
        final Party lender = output.getLender();
        final Party borrower = output.getBorrower();

        if (output.getValue() <= 0) {
            throw new IllegalArgumentException("The IOU's value must be non-negative.");
        }

        if (lender.equals(borrower)) {
            throw new IllegalArgumentException("The lender and the borrower cannot be the same entity.");
        }

        // Constraints on the signers.
        final List<PublicKey> requiredSigners = command.getSigners();
        final List<PublicKey> expectedSigners = Arrays.asList(borrower.getOwningKey(), lender.getOwningKey());

        if (requiredSigners.size() != 2) {
            throw new IllegalArgumentException("There must be two signers.");
        }

        if (!(requiredSigners.containsAll(expectedSigners))) {
            throw new IllegalArgumentException("The borrower and lender must both be signers.");
        }
    }

    // Used to indicate the transaction's intent.
    public interface Commands extends CommandData {
        class Action implements Commands {}
    }
}