package smarthand.ui_explorer.trace;

import smarthand.ui_explorer.Options;
import smarthand.ui_explorer.util.HashConsed;
import smarthand.ui_explorer.util.HashConsingFactory;
import smarthand.ui_explorer.util.Util;

/**
 * Created by wtchoi on 10/4/16.
 */
//TODO: hashconsing
public class Action extends HashConsed {
    public enum Kind {
        Start, Event, Restart, Close;

        public static Kind get(String kind) {
            if (kind.equals("Start")) return Start;
            if (kind.equals("Event")) return Event;
            if (kind.equals("Restart")) return Restart;
            if (kind.equals("Close")) return Close;
            throw new RuntimeException("Something is wrong");
        }
    }
    public Kind kind;
    public Integer actionIndex;

    private static HashConsingFactory<Action> factory = new HashConsingFactory<>(
            Action::checkEqual,
            new HashConsingFactory.InstanceDumper<Action>(Options.get(Options.Keys.IMAGE_OUTPUT_DIR), "event"));

    public static boolean checkEqual(Object s1, Object s2) {
        if (!(s1 instanceof Action) || !(s1 instanceof Action)) {
            return s1.equals(s2);
        }
        Action u1 = (Action) s1;
        Action u2 = (Action) s2;

        if (!u1.kind.equals(u2.kind)) return false;
        if (u1.kind.equals(Kind.Event)) {
            if (!Util.equalsNullable(u1.actionIndex, u2.actionIndex)) return false;
        }
        return true;
    }

    private Action(Kind kind, Integer actionIndex) {
        super(Util.chainHash(0, kind, actionIndex));

        // Assertions
        if (kind != Kind.Event) {
            if (actionIndex != null) {
                System.out.println(kind + ":" + actionIndex);
                throw new RuntimeException("Something is wrong!");
            }
        }
        else {
            if (actionIndex == null) {
                System.out.println(kind + ":" + actionIndex);
                throw new RuntimeException("Something is wrong!");
            }
        }

        // Assignments
        this.kind = kind;
        this.actionIndex = actionIndex;
    }

    @Override
    public String toString() {
        String result = kind.toString();
        if (kind.equals(Kind.Event)) result += (":" + actionIndex);
        return result;
    }

    public static Action getAction(Kind kind, Integer actionIndex) {
        return factory.getInstance(new Action(kind, actionIndex));
    }

    public static Action getEvent(Integer actionIndex) {
        return factory.getInstance(new Action(Kind.Event, actionIndex));
    }

    public static Action getStart() {
        return factory.getInstance(new Action(Kind.Start, null));
    }

    public static Action getClose() {
        return factory.getInstance(new Action(Kind.Close, null));
    }

    public static Action getReset() {
        return factory.getInstance(new Action(Kind.Restart, null));
    }

    public boolean isClose() {
        return this.kind.equals(Kind.Close);
    }

    public boolean isStart() {
        return this.kind.equals(Kind.Start);
    }

    public boolean isReset() {
        return this.kind.equals(Kind.Restart);
    }

    public boolean isEvent() {
        return this.kind.equals(Kind.Event);
    }
}
