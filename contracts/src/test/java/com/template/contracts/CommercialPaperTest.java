package com.template.contracts;

import kotlin.Unit;
import net.corda.core.contracts.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.utilities.OpaqueBytes;
import net.corda.finance.contracts.asset.Cash;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Currency;
import java.util.Locale;

import static com.template.contracts.CommercialPaper.CP_CONTRACT_ID;
import static net.corda.finance.Currencies.DOLLARS;
import static net.corda.finance.Currencies.issuedBy;
import static net.corda.testing.internal.InternalTestConstantsKt.TEST_TX_TIME;
import static net.corda.testing.node.NodeTestUtils.ledger;
import static net.corda.testing.node.NodeTestUtils.transaction;

/**
 * This doesn't seem to work inside Intellij, or with gradle test on the command line. From cordapp-template-java run:
 * ./gradlew test
 */
public class CommercialPaperTest {

    private TestIdentity megaCorp = new TestIdentity(new CordaX500Name("MegaCorp", "London", "GB"));
    private TestIdentity miniCorp = new TestIdentity(new CordaX500Name("MiniCorp", "London", "GB"));
    private static final TestIdentity bigCorp = new TestIdentity(new CordaX500Name("BigCorp", "New York", "US"));
    private MockServices ledgerServices = new MockServices(Collections.singletonList("net.corda.finance"), megaCorp, miniCorp);
    private byte[] defaultRef = new byte[] { (byte)0x01 };

    private OwnableState getPaper() {
        PartyAndReference partyAndReference = new PartyAndReference((AbstractParty) this.megaCorp.getParty(), OpaqueBytes.of((byte) 0));
        Amount<Issued<Currency>> issuedAmount = Amount.fromDecimal(new BigDecimal(1000), new Issued<Currency> (partyAndReference, Currency.getInstance(Locale.US)));
        return (OwnableState) new CommercialPaper.State(this.megaCorp.ref((byte) 123), this.megaCorp.getParty(), issuedAmount, Instant.now().plus(7, ChronoUnit.DAYS));
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void verify() {
    }

    @Test
    public void generateIssue() {
    }

    @Test
    public void generateMove() {
    }

    @Test
    public void generateRedeem() {
    }

    @Test
    public void emptyLedger() {
        ledger(this.ledgerServices, l -> {
            return null;
        });
    }

    // A transaction must contain at least one command
    @Test(expected = IllegalStateException.class)
    public void simpleCP() {

        OwnableState inState = getPaper();

        ledger(this.ledgerServices, l -> {
            l.transaction(tx -> {
                tx.attachments(CP_CONTRACT_ID);
                tx.input(CP_CONTRACT_ID, inState);
                return tx.verifies();
            });

            return Unit.INSTANCE;
        });
    }

    // This example test will fail with this exception.
    @Test(expected = TransactionVerificationException.ContractRejection.class)
    public void simpleCPMove() {

        OwnableState inState = getPaper();

        ledger(this.ledgerServices, l -> {
            l.transaction(tx -> {
                tx.attachments(CP_CONTRACT_ID);
                tx.input(CP_CONTRACT_ID, inState);
                tx.command(this.megaCorp.getPublicKey(), new CommercialPaper.Commands.Move());
                return tx.verifies();
            });

            return Unit.INSTANCE;
        });
    }

    @Test
    public void simpleCPMoveFails() {

        OwnableState inState = getPaper();

        ledger(this.ledgerServices, l -> {
            l.transaction(tx -> {
                tx.attachments(CP_CONTRACT_ID);
                tx.input(CP_CONTRACT_ID, inState);
                tx.command(this.megaCorp.getPublicKey(), new CommercialPaper.Commands.Move());
                return tx.failsWith("the state is propagated");
            });

            return Unit.INSTANCE;
        });
    }

    @Test
    public void simpleCPMoveSuccessAndFailure() {

        OwnableState inState = getPaper();

        ledger(this.ledgerServices, l -> {
            l.transaction(tx -> {
                tx.attachments(CP_CONTRACT_ID);
                tx.input(CP_CONTRACT_ID, inState);
                tx.command(this.megaCorp.getPublicKey(), new CommercialPaper.Commands.Move());
                tx.failsWith("the state is propagated");
                tx.output(CP_CONTRACT_ID, "MiniCorp's paper", inState.withNewOwner(this.miniCorp.getParty()).getOwnableState());
                return tx.verifies();
            });

            return Unit.INSTANCE;
        });
    }

    @Test
    public void simpleIssuanceWithTweak() {
        ledger(this.ledgerServices, l -> {
            l.transaction(tx -> {
                tx.output(CP_CONTRACT_ID, "paper", getPaper()); // Some CP is issued onto the ledger by MegaCorp.
                tx.attachments(CP_CONTRACT_ID);

                tx.tweak(tw -> {
                    tw.command(CommercialPaperTest.bigCorp.getPublicKey(), new CommercialPaper.Commands.Issue());
                    tw.timeWindow(TEST_TX_TIME);
                    return tw.failsWith("output states are issued by a command signer");
                });

                tx.command(this.megaCorp.getPublicKey(), new CommercialPaper.Commands.Issue());
                tx.timeWindow(TEST_TX_TIME);
                return tx.verifies();
            });

            return Unit.INSTANCE;
        });
    }

    // shortcut method for a single transaction test
    @Test
    public void simpleIssuanceWithTweakTopLevelTx() {
        transaction(this.ledgerServices, tx -> {
            tx.output(CP_CONTRACT_ID, "paper", getPaper()); // Some CP is issued onto the ledger by MegaCorp.
            tx.attachments(CP_CONTRACT_ID);

            tx.tweak(tw -> {
                tw.command(CommercialPaperTest.bigCorp.getPublicKey(), new CommercialPaper.Commands.Issue());
                tw.timeWindow(TEST_TX_TIME);
                return tw.failsWith("output states are issued by a command signer");
            });

            tx.command(this.megaCorp.getPublicKey(), new CommercialPaper.Commands.Issue());
            tx.timeWindow(TEST_TX_TIME);
            return tx.verifies();
        });
    }

    @Test
    public void chainCommercialPaper() {
        PartyAndReference issuer = this.megaCorp.ref(defaultRef);

        ledger(this.ledgerServices, l -> {
            l.unverifiedTransaction(tx -> {
                tx.output(Cash.PROGRAM_ID, "MiniCorp's $900", new Cash.State(issuedBy(DOLLARS(900), issuer), this.miniCorp.getParty()));
                tx.attachments(Cash.PROGRAM_ID);
                return Unit.INSTANCE;
            });

            // Some CP is issued onto the ledger by MegaCorp.
            l.transaction("Issuance", tx -> {
                tx.output(CP_CONTRACT_ID, "paper", getPaper());
                tx.command(this.megaCorp.getPublicKey(), new CommercialPaper.Commands.Issue());
                tx.attachments(CP_CONTRACT_ID);
                tx.timeWindow(TEST_TX_TIME);
                return tx.verifies();
            });

            l.transaction("Trade", tx -> {
                tx.input("paper");
                tx.input("MiniCorp's $900");
                tx.output(Cash.PROGRAM_ID, "borrowed $900", new Cash.State(issuedBy(DOLLARS(900), issuer), this.megaCorp.getParty()));
                CommercialPaper.State inputPaper = l.retrieveOutput(CommercialPaper.State.class, "paper");
                tx.output(CP_CONTRACT_ID, "MiniCorp's paper", inputPaper.withOwner(this.miniCorp.getParty()));
                tx.command(this.miniCorp.getPublicKey(), new Cash.Commands.Move());
                tx.command(this.megaCorp.getPublicKey(), new CommercialPaper.Commands.Move());
                return tx.verifies();
            });

            return Unit.INSTANCE;
        });
    }

    @Test
    public void chainCommercialPaperDoubleSpend() {
        PartyAndReference issuer = this.megaCorp.ref(defaultRef);

        ledger(this.ledgerServices, l -> {
            l.unverifiedTransaction(tx -> {
                tx.output(Cash.PROGRAM_ID, "MiniCorp's $900", new Cash.State(issuedBy(DOLLARS(900), issuer), this.miniCorp.getParty()));
                tx.attachments(Cash.PROGRAM_ID);
                return Unit.INSTANCE;
            });

            // Some CP is issued onto the ledger by MegaCorp.
            l.transaction("Issuance", tx -> {
                tx.output(CP_CONTRACT_ID, "paper", getPaper());
                tx.command(this.megaCorp.getPublicKey(), new CommercialPaper.Commands.Issue());
                tx.attachments(CP_CONTRACT_ID);
                tx.timeWindow(TEST_TX_TIME);
                return tx.verifies();
            });

            l.transaction("Trade", tx -> {
                tx.input("paper");
                tx.input("MiniCorp's $900");
                tx.output(Cash.PROGRAM_ID, "borrowed $900", new Cash.State(issuedBy(DOLLARS(900), issuer), this.megaCorp.getParty()));
                CommercialPaper.State inputPaper = l.retrieveOutput(CommercialPaper.State.class, "paper");
                tx.output(CP_CONTRACT_ID, "MiniCorp's paper", inputPaper.withOwner(this.miniCorp.getParty()));
                tx.command(this.miniCorp.getPublicKey(), new Cash.Commands.Move());
                tx.command(this.megaCorp.getPublicKey(), new CommercialPaper.Commands.Move());
                return tx.verifies();
            });

            l.transaction(tx -> {
                tx.input("paper");
                CommercialPaper.State inputPaper = l.retrieveOutput(CommercialPaper.State.class, "paper");

                // We moved a paper to another pubkey.
                tx.output(CP_CONTRACT_ID, "BigCorp's paper", inputPaper.withOwner(CommercialPaperTest.bigCorp.getParty()));
                tx.command(this.megaCorp.getPublicKey(), new CommercialPaper.Commands.Move());
                return tx.verifies();
            });

            l.fails();
            return Unit.INSTANCE;
        });
    }

    @Test
    public void chainCommercialPaperTweak() {
        PartyAndReference issuer = this.megaCorp.ref(defaultRef);

        ledger(this.ledgerServices, l -> {
            l.unverifiedTransaction(tx -> {
                tx.output(Cash.PROGRAM_ID, "MiniCorp's $900", new Cash.State(issuedBy(DOLLARS(900), issuer), this.miniCorp.getParty()));
                tx.attachments(Cash.PROGRAM_ID);
                return Unit.INSTANCE;
            });

            // Some CP is issued onto the ledger by MegaCorp.
            l.transaction("Issuance", tx -> {
                tx.output(CP_CONTRACT_ID, "paper", getPaper());
                tx.command(this.megaCorp.getPublicKey(), new CommercialPaper.Commands.Issue());
                tx.attachments(CP_CONTRACT_ID);
                tx.timeWindow(TEST_TX_TIME);
                return tx.verifies();
            });

            l.transaction("Trade", tx -> {
                tx.input("paper");
                tx.input("MiniCorp's $900");
                tx.output(Cash.PROGRAM_ID, "borrowed $900", new Cash.State(issuedBy(DOLLARS(900), issuer), this.megaCorp.getParty()));
                CommercialPaper.State inputPaper = l.retrieveOutput(CommercialPaper.State.class, "paper");
                tx.output(CP_CONTRACT_ID, "MiniCorp's paper", inputPaper.withOwner(this.miniCorp.getParty()));
                tx.command(this.miniCorp.getPublicKey(), new Cash.Commands.Move(CommercialPaper.class));
                tx.command(this.megaCorp.getPublicKey(), new CommercialPaper.Commands.Move());
                return tx.verifies();
            });

            l.tweak(lw -> {
                lw.transaction(tx -> {
                    tx.input("paper");
                    CommercialPaper.State inputPaper = l.retrieveOutput(CommercialPaper.State.class, "paper");

                    // We moved a paper to another pubkey.
                    tx.output(CP_CONTRACT_ID, "BigCorp's paper", inputPaper.withOwner(CommercialPaperTest.bigCorp.getParty()));
                    tx.command(this.megaCorp.getPublicKey(), new CommercialPaper.Commands.Move());
                    return tx.verifies();
                });

                lw.fails();
                return Unit.INSTANCE;
            });

            l.verifies();
            return Unit.INSTANCE;
        });
    }
}