package net.yacy.cora.ai.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.yacy.cora.ai.greedy.AbstractFinding;
import net.yacy.cora.ai.greedy.AbstractModel;
import net.yacy.cora.ai.greedy.Finding;
import net.yacy.cora.ai.greedy.Model;
import net.yacy.cora.ai.greedy.Role;

public class SchwarzerPeter {

    public static enum Kartentyp {
        A, B, C, D, E, F, G, H, P;
    }

    public static enum Kartenzaehler {
        p, q;
    }

    public static class Karte {
        private final Kartentyp kartentyp;
        private final Kartenzaehler kartenzaehler;
        public Karte(Kartentyp kartentyp, Kartenzaehler kartenzaehler) {
            this.kartentyp = kartentyp; this.kartenzaehler = kartenzaehler;
        }
        @Override
        public boolean equals(Object obj) {
            return obj != null
                    && obj instanceof Karte
                    && this.kartentyp == ((Karte) obj).kartentyp
                    && this.kartenzaehler == ((Karte) obj).kartenzaehler;
        }
        @Override
        public int hashCode() {
            return this.kartentyp.hashCode() + 16 + this.kartenzaehler.hashCode();
        }
        public boolean istSchwarzerPeter() {
            return this.kartentyp == Kartentyp.P;
        }
        public static boolean istPaar(Karte k1, Karte k2) {
            return k1.kartentyp == k2.kartentyp;
        }
    }

    public static final List<Karte> alleKarten;
    static {
        alleKarten = new ArrayList<Karte>(33);
        for (Kartentyp typ: Kartentyp.values()) {
            alleKarten.add(new Karte(typ, Kartenzaehler.p));
            alleKarten.add(new Karte(typ, Kartenzaehler.q));
        }
        alleKarten.add(new Karte(Kartentyp.P, Kartenzaehler.p));
    }

    public static final List<Karte> neuerStapel(Random r) {
        List<Karte> stapel0 = new ArrayList<Karte>();
        for (Karte karte: alleKarten) stapel0.add(karte);
        List<Karte> stapel1 = new ArrayList<Karte>();
        while (!stapel0.isEmpty()) stapel1.add(stapel0.remove(r.nextInt(stapel0.size())));
        return stapel1;
    }

    public static class Spieler implements Role {

        private final int spielernummer;
        private final int spieleranzahl;

        public Spieler(int spielernummer, int spieleranzahl) {
            this.spielernummer = spielernummer;
            this.spieleranzahl = spieleranzahl;
        }

        @Override
        public Spieler nextRole() {
            int n = (this.spielernummer == this.spieleranzahl - 1) ? 0 : this.spielernummer + 1;
            return new Spieler(n, this.spieleranzahl);
        }
        public Spieler linkerNachbar() {
            int n = (this.spielernummer == 0) ? this.spieleranzahl - 1 : this.spielernummer - 1;
            return new Spieler(n, this.spieleranzahl);
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null
                    && obj instanceof Spieler
                    && this.spielernummer == ((Spieler) obj).spielernummer;
        }

        @Override
        public int hashCode() {
            return this.spielernummer;
        }
    }

    public static enum Strategy {
        nichtsortieren_linksziehen,
        nichtsortieren_zufallsziehen,
        sortieren_linksziehen,
        sortieren_zufallsziehen;
    }

    public static class Hand extends ArrayList<Karte> {
        private static final long serialVersionUID = -5274023237476645059L;
        private final Strategy strategy;
        public Hand(Strategy strategy) {
            this.strategy = strategy;
        }
        public void annehmen(Random r, Karte karte) {
            if (this.strategy == Strategy.nichtsortieren_linksziehen || this.strategy == Strategy.nichtsortieren_zufallsziehen) {
                this.add(this.set(r.nextInt(this.size()), karte));
            } else {
                this.add(karte);
            }
        }
        public Karte abgeben(Random r) {
            if (this.strategy == Strategy.nichtsortieren_linksziehen || this.strategy == Strategy.sortieren_linksziehen) {
                return this.remove(0);
            }
            return this.remove(r.nextInt(this.size()));
        }
        public boolean paerchenAblegen() {
return true;
        }
    }

    public static class Zug extends AbstractFinding<Spieler> implements Finding<Spieler> {

        public Zug(Spieler spieler, int priority) {
            super(spieler, priority);
        }

        @Override
        public Object clone() {
            return this;
        }

        @Override
        public boolean equals(Object other) {
            return true;
        }

        @Override
        public int hashCode() {
            return 0;
        }

    }

    public static class Spiel extends AbstractModel<Spieler, Zug> implements Model<Spieler, Zug>, Cloneable {

        private final Hand[] haende;
        private final Random random;

        public Spiel(Spieler spieler, Random r) {
            super(spieler);
            this.random = r;
            this.haende = new Hand[spieler.spieleranzahl];
            for (int i = 0; i < spieler.spieleranzahl; i++) this.haende[i] = new Hand(Strategy.nichtsortieren_linksziehen);
            List<Karte> geben = neuerStapel(r);
            while (!geben.isEmpty()) {
                this.haende[spieler.spielernummer].annehmen(r, geben.remove(0));
                spieler = spieler.nextRole();
            }
        }

        @Override
        public List<Zug> explore() {
            return new ArrayList<Zug>(0);
        }

        @Override
        public void applyFinding(Zug finding) {
            this.haende[this.currentRole().spielernummer].annehmen(this.random, this.haende[this.currentRole().linkerNachbar().spielernummer].abgeben(this.random));

        }

        @Override
        public int getRanking(int findings, Spieler role) {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public boolean isTermination(Spieler role) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public Spieler isTermination() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Object clone() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean equals(Object other) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public int hashCode() {
            // TODO Auto-generated method stub
            return 0;
        }
    }
}
