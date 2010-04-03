package zoogas.core.rules;

import java.util.Set;
import java.util.HashSet;

import java.io.*;

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
        try {
            FileInputStream fis = new FileInputStream(filename);
            return fromStream(fis);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
