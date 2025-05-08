package com.function;

import com.function.util.WalletUtil;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.sql.*;
import java.util.Optional;

public class EliminarRolConReasignacion {

    @FunctionName("EliminarRolConReasignacion")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.DELETE},
            authLevel = AuthorizationLevel.ANONYMOUS,
            route = "rol/{id}"
        )
        HttpRequestMessage<Optional<String>> request,
        @BindingName("id") String id,
        final ExecutionContext context
    ) {
        context.getLogger().info("Intentando eliminar rol con id: " + id);

        // No se permite la eliminacion de roles usuario y administrador
        if ("1".equals(id) || "2".equals(id)) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("No está permitido eliminar los roles de administrador ni de usuario.")
                .build();
        }

        try {
            WalletUtil.copyWalletToTemp(System.getProperty("java.io.tmpdir"), context);
        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al preparar el wallet: " + e.getMessage())
                .build();
        }

        String tmpDir = System.getProperty("java.io.tmpdir").replace("\\", "/");
        String oracleUrl = "jdbc:oracle:thin:@et2xa97ns8rti1vt_tp?TNS_ADMIN=" + tmpDir;
        String oracleUser = "duoc_fullstack";
        String oraclePass = "Eduardocr#2610";

        try (Connection conn = DriverManager.getConnection(oracleUrl, oracleUser, oraclePass)) {

            PreparedStatement checkRol = conn.prepareStatement("SELECT 1 FROM ROLES WHERE ID = ?");
            checkRol.setString(1, id);
            ResultSet rs = checkRol.executeQuery();

            if (!rs.next()) {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                    .body("No se encontró rol con ID: " + id)
                    .build();
            }

            // se asigna el rol usuario por defecto
            PreparedStatement updateUsers = conn.prepareStatement(
                "UPDATE USUARIO SET ROL_ID = 2 WHERE ROL_ID = ?"
            );
            updateUsers.setString(1, id);
            int updated = updateUsers.executeUpdate();
            context.getLogger().info("Usuarios reasignados a rol usuario: " + updated);

            PreparedStatement deleteRol = conn.prepareStatement("DELETE FROM ROLES WHERE ID = ?");
            deleteRol.setString(1, id);
            int deleted = deleteRol.executeUpdate();

            if (deleted > 0) {
                return request.createResponseBuilder(HttpStatus.OK)
                    .body("Rol eliminado exitosamente.")
                    .build();
            } else {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("No se pudo eliminar el rol con ID: " + id)
                    .build();
            }

        } catch (SQLException e) {
            context.getLogger().severe("Error SQL: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al eliminar el rol: " + e.getMessage())
                .build();
        }
    }
}
