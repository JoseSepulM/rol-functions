package com.function;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;

import com.function.util.WalletUtil;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

public class EliminarRol {

    @FunctionName("EliminarRol")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.DELETE},
            authLevel = AuthorizationLevel.ANONYMOUS,
            route = "roles/eliminar/{id}"
        )
        HttpRequestMessage<Optional<String>> request,
        @BindingName("id") Long id,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java HTTP trigger processed a request to delete a role with ID: " + id);

        // 1. Copiar los archivos del wallet al directorio temporal
        try {
            WalletUtil.copyWalletToTemp(System.getProperty("java.io.tmpdir"), context);
        } catch (Exception e) {
            context.getLogger().severe("Error al copiar wallet: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al preparar el wallet: " + e.getMessage())
                .build();
        }

        // 2. Datos de conexión a Oracle
        String tmpDir = System.getProperty("java.io.tmpdir");
        String walletPath = tmpDir.contains("\\") ? tmpDir.replace("\\", "/") : tmpDir;

        String oracleUrl = "jdbc:oracle:thin:@et2xa97ns8rti1vt_tp?TNS_ADMIN=" + walletPath;
        String oracleUser = "duoc_fullstack";
        String oraclePass = "Eduardocr#2610";

        // 3. Eliminar el rol
        try (Connection conn = DriverManager.getConnection(oracleUrl, oracleUser, oraclePass)) {
            String sql = "DELETE FROM ROLES WHERE ID = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setLong(1, id);

            int rowsDeleted = stmt.executeUpdate();

            if (rowsDeleted > 0) {
                return request.createResponseBuilder(HttpStatus.OK)
                    .body("Rol eliminado exitosamente.")
                    .build();
            } else {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                    .body("No se encontró el rol con ID: " + id)
                    .build();
            }
        } catch (SQLException e) {
            context.getLogger().severe("Error SQL al eliminar rol: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al eliminar el rol: " + e.getMessage())
                .build();
        }
    }
}
