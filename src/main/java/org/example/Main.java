package org.example;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.bitcoinj.base.*;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class Main {

    public static void main(String[] args) {
        NetworkParameters networkParameters = TestNet3Params.get();
        Network network = networkParameters.network();
        WalletAppKit kit = WalletAppKit.launch((BitcoinNetwork) network, new File("."), "walletappkit-test", (k) -> {
//            k.connectToLocalHost();
        });
        DeterministicSeed seed = kit.wallet().getKeyChainSeed();
        ECKey key = kit.wallet().freshReceiveKey();

        Address address = LegacyAddress.fromKey(TestNet3Params.get(), key);
        String privateKeyWIF = key.getPrivateKeyEncoded(TestNet3Params.get()).toBase58();
        System.out.println("\ncreation time: " + seed.creationTime().get().getEpochSecond());
        System.out.println("Wallet {" +
                "\n     publicAddress=" + address + ", " +
                "\n     privateKey=" + privateKeyWIF + ", " +
                "\n     mnemonicString=" + seed.getMnemonicString() + ", " +
                "\n     seed=" + seed + ", " +
                "\n}");
        System.out.println("Wallet balance before transaction: " + kit.wallet().getBalance().toFriendlyString());

        kit.wallet().addCoinsReceivedEventListener((w, tx, prevBalance, newBalance) -> {
            System.out.println("-----> coins resceived: " + tx.getTxId());
            System.out.println("received: " + tx.getValue(kit.wallet()));
        });

        kit.wallet().addCoinsSentEventListener((w, tx, prevBalance, newBalance) -> System.out.println("coins sent"));

        kit.wallet().addKeyChainEventListener(keys -> System.out.println("new key added"));

        kit.wallet().addScriptsChangeEventListener((w, scripts, isAddingScripts) -> System.out.println("new script added"));

        kit.wallet().addTransactionConfidenceEventListener((w, tx) -> {
            System.out.println("-----> confidence changed: " + tx.getTxId());
            TransactionConfidence confidence = tx.getConfidence();
            System.out.println("new block depth: " + confidence.getDepthInBlocks());
        });

        kit.wallet().addCoinsReceivedEventListener((wallet, tx, prevBalance, newBalance) -> {
                    Coin value = tx.getValueSentToMe(wallet);
                    System.out.println("Received tx for " + value.toFriendlyString());
                    Futures.addCallback(tx.getConfidence().getDepthFuture(1),
                            new FutureCallback<TransactionConfidence>() {
                                @Override
                                public void onSuccess(TransactionConfidence result) {
                                    System.out.println("Received tx " +
                                            value.toFriendlyString() + " is confirmed. ");
                                    System.out.println("Wallet balance after transaction: " +
                                            kit.wallet().getBalance().toFriendlyString());

                                }
                                @Override
                                public void onFailure(Throwable t) {}
                            }, MoreExecutors.directExecutor());
                });

        //TODO tb1qyx29szp6n4qgf69rya49mk2rdmajpmczuhmm4x - 0,00001 tests bitcoins
        // You can use a faucet to get testnet coins and send some coins to that address and see if everything works.
        // f.e. https://bitcoinfaucet.uo1.net/send.php

        Coin value = Coin.parseCoin("0.00001");
        System.out.println("Send money to: " + address);

        try {
            Wallet.SendResult result = kit.wallet().sendCoins(kit.peerGroup(), address, value);
            System.out.println("coins sent. transaction hash: " + result.transaction().getTxId());
        } catch (InsufficientMoneyException e) {
            System.out.println("Not enough coins in your wallet. Missing " + e.missing.getValue() + " satoshis are missing (including fees)");
            CompletableFuture<Coin> balanceFuture = kit.wallet().getBalanceFuture(value, Wallet.BalanceType.AVAILABLE);
            balanceFuture.whenComplete((balance, throwable) -> {
                if (balance != null) {
                    System.out.println("coins arrived and the wallet now has enough balance");
                } else {
                    System.out.println("something went wrong");
                }
            });
        }

//        System.out.println("shutting down ...");
//        kit.stopAsync();
//        kit.awaitTerminated();
    }
}