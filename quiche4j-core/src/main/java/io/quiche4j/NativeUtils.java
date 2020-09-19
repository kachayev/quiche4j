package io.quiche4j;

import java.io.IOException;
import java.net.URL;

public final class NativeUtils {

    private static final String DEFAUL_DIR = "/native-libs/";

    private static final String[] ALLOWED_EXTENTIONS = new String[]{"so", "dylib", "dll"};

    public static void loadEmbeddedLibrary(String libname) {
        loadEmbeddedLibrary(DEFAUL_DIR, libname);
    }

    public static void loadEmbeddedLibrary(String dir, String libname) {
        final String filename = "lib" + libname;

        String nativeLibraryFilepath = null;
        for (String ext: ALLOWED_EXTENTIONS) {
            final String filepath = dir + filename + "." + ext;
            final URL url = Quiche.class.getResource(filepath);
            if (url != null) {
                nativeLibraryFilepath = filepath;
                break;
            }
        }

        if (nativeLibraryFilepath != null) {
            // native library found within JAR, extract and load
            try {
                final String libfile = Utils.copyFileFromJAR("libs", nativeLibraryFilepath);
                System.load(libfile);
            } catch (IOException e) {
                // no-op
            }
        }
    }
}