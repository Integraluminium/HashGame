package hashGame;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.*;
import java.io.*;

public class HashClient {

    public final MessageDigest 	digest;
    public final String 		name;
    public       int			difficulty;

    HashClient(String aHash,String aName) throws NoSuchAlgorithmException
    {
        digest     = MessageDigest.getInstance(aHash);
        difficulty = 0;
        name       = aName;
    }

    public String getLine(final String parent,final String seed)
    {
        return parent+" "+name+" "+seed;
    }

    public byte [] getHash(final String parent,final String seed)
    {
        return digest.digest(getLine(parent,seed).getBytes());
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

    static public String toHex(byte [] hash)
    {
        return bytesToHex(hash);
    }

    public int countZerobits(final byte [] input)
    {
        int bits=0;
        int i;
        for(i=0;i<input.length && input[i]==0;i++)
            bits+=8;

        if(i<input.length)
        {
            byte remain=input[i];
            while(remain!=0 && (remain&0x80)==0)
            {
                bits++;
                remain=(byte) ((remain&0x7f)<<1);
            }
        }
        return bits;
    }

    long lastGetParent=0;

    public String getParent(String url)
    {
        // Do not hit the server too often to avoid being locked out.
        if(System.currentTimeMillis()-lastGetParent<2000) // Prevent being locked out
        {
            try {
                Thread.sleep(2100-(System.currentTimeMillis()-lastGetParent));
            }
            catch (Exception e)
            {
            }
        }
        lastGetParent=System.currentTimeMillis();

        System.out.println();
        System.out.println(url);
        try {
            int level=0;
            String parent="";

            URL server = new URL(url);
            URLConnection yc = server.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    yc.getInputStream()));
            String inputLine=in.readLine();
            System.out.println(inputLine);
            if(inputLine!=null)
            {
                int newDifficulty=Integer.parseInt(inputLine);
                if(newDifficulty!=difficulty)
                {
                    System.out.println("Difficulty: "+newDifficulty);
                    difficulty=newDifficulty;
                }
            }

            while ((inputLine = in.readLine()) != null)
            {
                System.out.println(inputLine);
                String[] sarray=inputLine.split("\\t");
                if(Integer.parseInt(sarray[1])>=level)
                {
                    level=Integer.parseInt(sarray[1]);
                    parent=sarray[0];
                }
            }
            in.close();
            System.out.println();
            return parent;

        }
        catch (Exception e)
        {
            System.out.println("Failed.");
            System.exit(1);
        }
        return "";
    }

    public String getLatestParent()
    {
        return getParent("http://hash.h10a.de/?raw");
    }

    public String findSeed(String parent)
    {
        String seed;
        boolean done=false;
        int best=0;
        do {

            seed=Long.toHexString(Double.doubleToLongBits(Math.random())); // max 64 bits

            byte [] hash=getHash(parent,seed);

            int count=countZerobits(hash);

            if(count>=difficulty)
            {
                System.out.println(" Done: "+count+" "+toHex(hash));
                done=true;
            }
            else if(count>best)
            {
                best=count;
                System.out.println(" Best: "+count+" "+toHex(hash));
            }

        } while(!done);
        return seed;
    }

    public String sendSeed(String parent,String seed)
    {
        return getParent("http://hash.h10a.de/?raw&Z="+parent+"&P="+name+"&R="+seed);
    }

    public String getHashString(String parent,String seed)
    {
        return bytesToHex(digest.digest(getLine(parent,seed).getBytes()));
    }

    public static void main(String[] args)
    {

        try
        {
            // nickname below ***********
            String name="Integraluminium";
            // nickname above ***********

            name = name + "-B2";
            final HashClient hasher=new HashClient("SHA-256",name);

            System.out.println("Using name: "+name);

            String parent=hasher.getLatestParent();
            String seed;

            while(true)
            {
                seed=hasher.findSeed(parent);
                hasher.sendSeed(parent,seed);
                parent=toHex(hasher.getHash(parent,seed));
            }

        } catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }
    }
}