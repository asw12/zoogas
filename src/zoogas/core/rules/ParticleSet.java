package zoogas.core.rules;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.io.OutputStream;

import java.io.PrintStream;

import java.util.Set;
import java.util.HashSet;

import zoogas.core.Board;
import zoogas.core.Particle;

public class ParticleSet {
    protected Set<String> particleName = new HashSet<String>();

    // method to convert names to particles
    public Set<Particle> getParticles(Board board) {
        Set<Particle> ps = new HashSet<Particle>();
        for (String e : particleName)
            ps.add(board.getOrCreateParticle(e));
        return ps;
    }

    // i/o
    void toStream(OutputStream out) {
        PrintStream print = new PrintStream(out);
        for (String e : particleName)
            print.println(e);
        print.println("END");
        print.close();
    }

    void toFile(String filename) {
        try {
            FileOutputStream fos = new FileOutputStream(filename);
            toStream(fos);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ParticleSet fromStream(InputStream in) {
        ParticleSet ps = new ParticleSet();
        InputStreamReader read = new InputStreamReader(in);
        BufferedReader buff = new BufferedReader(read);
        try {
            while (buff.ready()) {
                String s = buff.readLine();
                if (s.equals("END"))
                    break;
                else
                    ps.particleName.add(s);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return ps;
    }

    public static ParticleSet fromFile(String filename) {
        InputStream fis = null;
        try {
            fis = new FileInputStream(filename);
        }
        catch (FileNotFoundException e) {
            fis = ClassLoader.getSystemClassLoader().getResourceAsStream(filename);
        }
        
        return fromStream(fis);
    }
}
