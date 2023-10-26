package hashGame;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;


public class HashClient3 implements Runnable{
    public final MessageDigest digest;
    public final String name;
    public static volatile int difficulty;

    private static volatile String namehash = "";
    private final int instance;
    private static int instanceCounter = 0;

    HashClient3(String aHash, String aName) throws NoSuchAlgorithmException {
        digest = MessageDigest.getInstance(aHash);
        difficulty = 0;
        name = aName;
        instance = instanceCounter++;
    }

    public String getLine(final String parent, final String seed) {
        return parent + " " + name + " " + seed;
    }

    public byte[] getHash(final String parent, final String seed) {
        return digest.digest(getLine(parent, seed).getBytes());
    }

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    static public String toHex(byte[] hash) {
        return bytesToHex(hash);
    }

    public int countZerobits(final byte[] input) {
        int bits = 0;
        int i;
        for (i = 0; i < input.length && input[i] == 0; i++)
            bits += 8;

        if (i < input.length) {
            byte remain = input[i];
            while (remain != 0 && (remain & 0x80) == 0) {
                bits++;
                remain = (byte) ((remain & 0x7f) << 1);
            }
        }
        return bits;
    }

    long lastGetParent = 0;

    public String getParent(String url) {
//        System.err.println("parent");
        // Do not hit the server too often to avoid being locked out.
        if (System.currentTimeMillis() - lastGetParent < 2000) // Prevent being locked out
        {
            try {
                Thread.sleep(2100 - (System.currentTimeMillis() - lastGetParent));
            } catch (Exception ignored) {
            }
        }
        lastGetParent = System.currentTimeMillis();

//        System.out.println();
//        System.out.println(url);
        try {
            int level = 0;
            String parent = "";

            URL server = new URL(url);
            URLConnection yc = server.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    yc.getInputStream()));
            String inputLine = in.readLine();
//            System.out.println(inputLine);
            if (inputLine != null) {
                int newDifficulty = Integer.parseInt(inputLine);
                if (newDifficulty != difficulty) {
                    System.out.println("Difficulty: " + newDifficulty);
                    difficulty = newDifficulty;
                }
            }

            while ((inputLine = in.readLine()) != null) {
//                System.out.println(inputLine);
                String[] sarray = inputLine.split("\\t");
                if (Integer.parseInt(sarray[1]) >= level) {
                    level = Integer.parseInt(sarray[1]);
                    parent = sarray[0];
                }
            }
            in.close();
//            System.out.println();
            if(!Objects.equals(namehash, parent)){
                System.out.println();
                System.out.println("New parent: " + parent + "\t" + level);
                System.out.println();
            }
            return parent;

        } catch (Exception e) {
            System.out.println("Failed.");
            System.exit(1);
        }
        return "";
    }

    public String getLatestParent() {
        return getParent("http://hash.h10a.de/?raw");
    }

    public String findSeed(String parent) {
        String seed;
        boolean done = false;
        int best = 0;
        do {

            seed = Long.toHexString(Double.doubleToLongBits(Math.random())); // max 64 bits

            byte[] hash = getHash(parent, seed);

            int count = countZerobits(hash);

            if (count >= difficulty) {
                System.out.println(" Done: " + count + " " + toHex(hash));
                done = true;
            } else if (count > best) {
                best = count;
                if (count > 20) {
                    System.out.println(" Best"+instance+": " + count + " " + toHex(hash));
                }
            }

        } while (!done);
        return seed;
    }

    public String sendSeed(String parent, String seed) {
        return getParent("http://hash.h10a.de/?raw&Z=" + parent + "&P=" + name + "&R=" + seed);
    }

    public String getHashString(String parent, String seed) {
        return bytesToHex(digest.digest(getLine(parent, seed).getBytes()));
    }


    @Override
    public void run() {
        HashClient3 hasher = this;
        String parent = namehash;
        String seed;

        while (true) {
            seed = hasher.findSeed(parent);
            System.err.println("seed");
            hasher.sendSeed(parent, seed);
            namehash = toHex(hasher.getHash(parent, seed));
        }
    }

    public void startController(){
        while (true){
            try {
                Thread.sleep(5000);
                namehash = getLatestParent();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }
    }

    public static void main(String[] args) {

        try {
            // nickname below ***********
            String name="Test";
//            String name = "SimpleChat";
            final int cores = 11;
            // nickname above ***********

            name = name + "-B2";
            System.out.println("Using name: " + name);
            HashClient3[] clients = new HashClient3[cores];
            for (int i = 0; i < cores; i++) {
                clients[i] = new HashClient3("SHA-256", name);
            }
            HashClient3 ctrl = new HashClient3("SHA-256", name);
            namehash = ctrl.getLatestParent();

            for (HashClient3 client : clients) {
                Thread fred = new Thread(client);
                fred.start();
            }
            ctrl.startController();



        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

}
