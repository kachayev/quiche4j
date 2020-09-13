package io.quiche4j;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

class NativeUtils {

    private static final String[] ALLOWED_EXTENTIONS = new String[]{"so", "dylib", "dll"};

    private static String getCurrentPlatformIdentifier() {
        String os = System.getProperty("os.name");
        if (os.toLowerCase().contains("windows")) {
            os = "windown";
        } else if (os.toLowerCase().contains("mac os x")) {
            os = "macosx";
        } else {
            os = os.replaceAll("\\s+", "_");
        }
        return os + "_" + System.getProperty("os.arch");
    }

    protected static void loadEmbeddedLibrary(String libname) {
        final String filename = "lib" + libname;

        // attempt to locate embedded native library within JAR at following location:
        // /NATIVE/${os.name}_${os.arch}/libquiche_jni.[so|dylib|dll]
        final StringBuilder url = new StringBuilder();
        url.append("/NATIVE/");
        url.append(getCurrentPlatformIdentifier()).append("/");

        URL nativeLibraryUrl = null;
        for(String ext: ALLOWED_EXTENTIONS) {
            nativeLibraryUrl = Quiche.class.getResource(url.toString() + filename + "." + ext);
            if (nativeLibraryUrl != null) break;
        }

        if (nativeLibraryUrl != null) {
            // native library found within JAR, extract and load
            try {
                final File libfile = File.createTempFile(filename, ".lib");
                libfile.deleteOnExit(); // just in case

                final InputStream in = nativeLibraryUrl.openStream();
                final OutputStream out = new BufferedOutputStream(new FileOutputStream(libfile));

                int len = 0;
                byte[] buffer = new byte[8192];
                while ((len = in.read(buffer)) > -1)
                    out.write(buffer, 0, len);
                out.close();
                in.close();
                System.load(libfile.getAbsolutePath());
            } catch (IOException e) {
                // mission failed, do nothing
            }
        }
    }
}