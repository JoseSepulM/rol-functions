package com.function;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.function.DTO.RolesDTO;
import com.function.util.WalletUtil;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

public class ActualizarRol {

    @FunctionName("ActualizarRol")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.PUT},
            authLevel = AuthorizationLevel.ANONYMOUS,
            route = "roles/actualizar/{id}"
        )
        HttpRequestMessage<Optional<String>> request,
        @BindingName("id") Long id,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java HTTP trigger processed a request to update a role with ID: " + id);

        // 1. Copiar los archivos del wallet al directorio temporal
        try {
            WalletUtil.copyWalletToTemp(System.getProperty("java.io.tmpdir"), context);
        } catch (Exception e) {
            context.getLogger().severe("Error al copiar wallet: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al preparar el wallet: " + e.getMessage())
                .build();
        }

        // 2. Leer el body (nuevo nombre de rol)
        String requestBody = request.getBody().orElse("");
        RolesDTO rolActualizado;
        try {
            ObjectMapper mapper = new ObjectMapper();
            rolActualizado = mapper.readValue(requestBody, RolesDTO.class);
        } catch (Exception e) {
            context.getLogger().severe("Error al leer el cuerpo de la solicitud: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("Error en el formato de los datos enviados.")
                .build();
        }

        // 3. Validación básica
        if (rolActualizado.getRol() == null || rolActualizado.getRol().isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("El campo 'rol' es obligatorio.")
                .build();
        }

        // 4. Datos de conexión a Oracle
        String tmpDir = System.getProperty("java.io.tmpdir");
        String walletPath = tmpDir.contains("\\") ? tmpDir.replace("\\", "/") : tmpDir;

        String oracleUrl = "jdbc:oracle:thin:@et2xa97ns8rti1vt_tp?TNS_ADMIN=" + walletPath;
        String oracleUser = "duoc_fullstack";
        String oraclePass = "Eduardocr#2610";

        // 5. Actualizar el rol
        try (Connection conn = DriverManager.getConnection(oracleUrl, oracleUser, oraclePass)) {
            String sql = "UPDATE ROLES SET ROL = ? WHERE ID = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, rolActualizado.getRol());
            stmt.setLong(2, id);

            int rowsUpdated = stmt.executeUpdate();

            if (rowsUpdated > 0) {
                return request.createResponseBuilder(HttpStatus.OK)
                    .body("Rol actualizado exitosamente.")
                    .build();
            } else {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                    .body("No se encontró el rol con ID: " + id)
                    .build();
            }
        } catch (SQLException e) {
            context.getLogger().severe("Error SQL al actualizar rol: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al actualizar el rol: " + e.getMessage())
                .build();
        }
    }
}
