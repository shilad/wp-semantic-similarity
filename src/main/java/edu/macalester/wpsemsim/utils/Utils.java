package edu.macalester.wpsemsim.utils;

import java.io.*;

public class Utils {

    static public Object readObject(File file) throws IOException {
        ObjectInputStream in = new ObjectInputStream(new FileInputStream(file));
        try {
            Object o = in.readObject();
            in.close();
            return o;
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    static public void writeObject(File file, Object obj) throws IOException {
        ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file));
        out.writeObject(obj);
        out.close();
    }
}
