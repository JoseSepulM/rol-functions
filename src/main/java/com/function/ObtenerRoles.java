package com.function;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.function.DTO.RolesDTO;
import com.function.util.WalletUtil;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

public class ObtenerRoles {

    @FunctionName("ObtenerRoles")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS,
            route = "roles/obtener"
        )
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java HTTP trigger processed a request to get roles.");

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

        // 3. Lógica de conexión y consulta
        try (Connection conn = DriverManager.getConnection(oracleUrl, oracleUser, oraclePass)) {
            String sql = "SELECT * FROM ROLES";
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();

            List<RolesDTO> roles = new ArrayList<>();

            while (rs.next()) {
                RolesDTO rol = new RolesDTO();
                rol.setId(rs.getLong("ID"));
                rol.setRol(rs.getString("ROL"));
                roles.add(rol);
            }

            if (roles.isEmpty()) {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                    .body("No se encontraron roles")
                    .build();
            } else {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(roles); // Convertimos toda la lista a JSON

                return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(json)
                    .build();
            }
        } catch (SQLException e) {
            context.getLogger().severe("Error SQL: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al consultar Oracle: " + e.getMessage())
                .build();
        } catch (JsonProcessingException e) {
            context.getLogger().severe("Error al convertir a JSON: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al convertir los datos a JSON: " + e.getMessage())
                .build();
        }
    }
}
