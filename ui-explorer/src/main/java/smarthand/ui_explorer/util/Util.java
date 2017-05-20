package smarthand.ui_explorer.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.*;

/**
 * Created by wtchoi on 4/12/16.
 */
public abstract class Util {
    public static void executeShellCommand(String cmd, LinkedList<String> buffer) {
        try {
            Runtime rt = Runtime.getRuntime();
            Process pr = rt.exec(cmd);

            BufferedReader dumpsysIn = new BufferedReader(new InputStreamReader(pr.getInputStream()));
            LinkedList<String> list = new LinkedList<>();
            String line;

            while((line=dumpsysIn.readLine()) != null) {
                list.add(line);
            }

            pr.waitFor();
            Thread.sleep(100);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void sleep(long msec) {
        try {
            Thread.sleep(msec);
        }
        catch (InterruptedException e) {
            System.out.println("Cannot sleep!");
            e.printStackTrace();
        }
    }

    public static void dumpStringToFile(String dir_path, String filename, String contents) {
        File dir = new File(dir_path);
        if (!dir.exists()) dir.mkdir();
        try {
            FileWriter fw = new FileWriter(dir_path + filename);
            fw.write(contents);
            fw.flush();
            fw.close();
        }
        catch (IOException e) { }
    }

    public static void appendStringToFile(String dir_path, String filename, String contents) {
        File dir = new File(dir_path);
        if (!dir.exists()) dir.mkdir();
        try {
            FileWriter fw = new FileWriter(dir_path + filename, true);
            fw.write(contents);
            fw.flush();
            fw.close();
        }
        catch (IOException e) { }
    }


    public static int compareIntegerList(List<Integer> o1, List<Integer> o2) {
        final int len1 = o1.size();
        final int len2 = o2.size();
        if (len1 < len2) return -1;
        if (len1 > len2) return 1;

        for (int i = 0; i < o1.size(); i++) {
            final int val1 = o1.get(i);
            final int val2 = o2.get(i);
            if (val1 < val2) return -1;
            if (val1 > val2) return 1;
        }

        return 0;
    }


    public static StringBuilder makeIntSetToString(Iterable<Integer> set, String delim, StringBuilder builder) {
        if (builder == null) builder = new StringBuilder();

        boolean flag = false;
        for(Integer id: set) {
            if (flag) builder.append(delim);
            else flag = true;
            builder.append(id);
        }

        return builder;
    }

    public static StringBuilder makeIntArrToString(Integer[] arr, String delim, StringBuilder builder) {
        if (builder == null) builder = new StringBuilder();

        boolean flag = false;
        for(int i=0; i<arr.length; i++) {
            if (flag) builder.append(delim);
            else flag = true;
            builder.append(arr[i]);
        }

        return builder;

    }

    public static JSONArray makeIntSetToJson(Iterable<Integer> set) {
        JSONArray result = new JSONArray();
        for (Integer elt : set) {
            result.put(elt);
        }
        return result;
    }

    public static HashSet<Integer> makeJsonToIntSet(JSONArray json) {
        HashSet<Integer> result = new HashSet<>();
        for (int i=0;i<json.length();i++) {
            result.add(json.getInt(i));
        }
        return result;
    }

    public static JSONObject readJsonFile(String path) {
        try {
            InputStream is = new FileInputStream(path);
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder builder = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                builder.append(line);
            }
            br.close();
            isr.close();
            is.close();

            JSONObject obj = new JSONObject(builder.toString());
            return obj;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeJsonFile(String path, JSONObject obj) {
        try (FileWriter file = new FileWriter(path)) {
            file.write(obj.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int[] permutation(int size, Random rand) {
        LinkedList<Integer> list = new LinkedList<>();
        for (int i=0; i<size; i++) {
            list.add(i);
        }

        int[] result = new int[size];
        for (int i=0; i<size; i++) {
            int index = rand.nextInt(list.size());
            result[i] = list.remove(index);
        }
        return result;
    }

    public static <T> T getRandomElement(Collection<T> collection, Random rand) {
        int index = rand.nextInt(collection.size());

        int cursor = 0;
        for (T elt: collection) {
            if (cursor == index) return elt;
            cursor++;
        }
        throw new RuntimeException("Something is weong");
    }

    public static int getRandomEvent(final int eventCount, Set<Integer> blackList, Random rand) {
        Integer size = blackList != null ? eventCount - blackList.size() : eventCount;
        if (size.equals(0)) return -1;

        int index =  rand.nextInt(size);
        int cur = 0;

        while(true) {
            if (blackList != null) {
                if (blackList.contains(cur)) {
                    cur++;
                }
            }

            if (index == 0) {
                break;
            }
            cur++;
            index--;
        }

        if (eventCount<= cur) {
            throw new RuntimeException("Something is wrong!");
        }
        return cur;
    }


    public static int chainHash(int base, Object... objs) {
        int hashcode = base;
        for (Object obj: objs) {
            hashcode = 31*hashcode + (obj==null ? 0 : obj.hashCode());
        }
        return hashcode;
    }

    public static int arrayChainHash(int base, Object[] arr) {
        int hashcode = base;
        if (arr == null) return base;
        for (Object obj : arr) {
            hashcode = 31*hashcode + (obj==null ? 0 : obj.hashCode());
        }
        return hashcode;
    }

    public static <E> HashSet<E> setMinus(Collection<E> a, Collection<E> b){
        HashSet<E> result = new HashSet<E>();
        a.forEach(x -> { if (!b.contains(x)) { result.add(x); } });
        return result;
    }

    public static <E> HashSet<E> setIntersect(Collection<E> a, Collection<E> b) {
        HashSet<E> result = new HashSet<E>();
        a.forEach(x -> { if (b.contains(x)) { result.add(x); } });
        return result;
    }

    public static boolean equalsNullable(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null && b != null) return false;
        if (a != null && b == null) return false;
        return a.equals(b);
    }
}

