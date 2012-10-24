package net.shipilev.elections.cikrf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Geography implements Comparable<Geography> {
    private static final Set<String> STOP_WORDS = new HashSet<String>() {{
        add("ЦИК России");
        add("Выборы и референдумы");
        add("сайт избирательной");
    }};

    private final List<String> coords;

    public Geography(Collection<String> c) {
        this.coords = new ArrayList<String>();
        for (String s : c) {
            if (!stopCoord(s)) {
                coords.add(s);
            }
        }
    }

    public static boolean stopCoord(String s) {
        if (s.trim().isEmpty()) {
            return true;
        }

        for (String stop : STOP_WORDS) {
            if (s.contains(stop)) {
                return true;
            }
        }

        return false;
    }

    public String get(int index) {
        if (index >= coords.size()) {
            return "";
        } else {
            return coords.get(index);
        }
    }

    public int size() {
        return coords.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Geography geography = (Geography) o;

        if (coords != null ? !coords.equals(geography.coords) : geography.coords != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = coords != null ? coords.hashCode() : 0;
        return result;
    }

    @Override
    public int compareTo(Geography o) {
        for (int c = 0; c < Math.max(size(), o.size()); c++) {
            int r = get(c).compareTo(o.get(c));
            if (r != 0) {
                return r;
            }
        }
        return 0;
    }

    public Geography aid(String uikName) {
        Geography c = new Geography(this.coords);
        c.addCoord(uikName);
        return c;
    }

    private void addCoord(String uikName) {
        coords.add(uikName);
    }

    @Override
    public String toString() {
        return "Geography{" +
                "coords=" + coords +
                '}';
    }
}
