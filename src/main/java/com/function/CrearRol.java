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

public class CrearRol {

    @FunctionName("CrearRol")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS,
            route = "roles/crear"
        )
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java HTTP trigger processed a request to create a role.");

        // 1. Copiar los archivos del wallet al directorio temporal
        try {
            WalletUtil.copyWalletToTemp(System.getProperty("java.io.tmpdir"), context);
        } catch (Exception e) {
            context.getLogger().severe("Error al copiar wallet: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al preparar el wallet: " + e.getMessage())
                .build();
        }

        // 2. Leer el body
        String requestBody = request.getBody().orElse("");
        RolesDTO nuevoRol;
        try {
            ObjectMapper mapper = new ObjectMapper();
            nuevoRol = mapper.readValue(requestBody, RolesDTO.class);
        } catch (Exception e) {
            context.getLogger().severe("Error al leer el cuerpo de la solicitud: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("Error en el formato de los datos enviados.")
                .build();
        }

        // 3. Validación básica
        if (nuevoRol.getRol() == null || nuevoRol.getRol().isEmpty()) {
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

        // 5. Insertar el nuevo rol
        try (Connection conn = DriverManager.getConnection(oracleUrl, oracleUser, oraclePass)) {
            String sql = "INSERT INTO ROLES (ROL) VALUES (?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, nuevoRol.getRol());
            int rowsInserted = stmt.executeUpdate();

            if (rowsInserted > 0) {
                return request.createResponseBuilder(HttpStatus.CREATED)
                    .body("Rol creado exitosamente.")
                    .build();
            } else {
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("No se pudo crear el rol.")
                    .build();
            }
        } catch (SQLException e) {
            context.getLogger().severe("Error SQL al crear rol: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al crear el rol: " + e.getMessage())
                .build();
        }
    }
}
