package com.function.util;

import com.microsoft.azure.functions.ExecutionContext;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class WalletUtil {

    public static void copyWalletToTemp(String destino, ExecutionContext context) throws IOException {
        String[] archivos = {
            "cwallet.sso", "ewallet.p12", "ewallet.pem", "keystore.jks",
            "ojdbc.properties", "sqlnet.ora", "tnsnames.ora", "truststore.jks"
        };

        for (String archivo : archivos) {
            try (InputStream in = WalletUtil.class.getClassLoader().getResourceAsStream("wallet/" + archivo)) {
                if (in == null) {
                    throw new FileNotFoundException("No se encontró el archivo en recursos: " + archivo);
                }
                File destinoArchivo = new File(destino, archivo);
                Files.copy(in, destinoArchivo.toPath(), StandardCopyOption.REPLACE_EXISTING);
                context.getLogger().info("Archivo copiado al temporal: " + destinoArchivo.getAbsolutePath());
            }
        }
    }
}
