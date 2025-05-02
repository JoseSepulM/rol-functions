package com.function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.function.DTO.RolesDTO;
import com.function.util.WalletUtil;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.sql.*;
import java.util.*;

public class ProcesarEventoCrearRol {
    
    @FunctionName("ProcesarEventoCrearRol")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS,
            route = "event/rol"
        ) HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        context.getLogger().info("Evento recibido por Webhook");

        try {
            ObjectMapper mapper = new ObjectMapper();
            String body = request.getBody().orElse("[]");

            JsonNode[] events = mapper.readValue(body, JsonNode[].class);

            for (JsonNode event : events) {
                String eventType = event.get("eventType").asText();

                if ("Microsoft.EventGrid.SubscriptionValidationEvent".equals(eventType)) {
                    String validationCode = event.get("data").get("validationCode").asText();

                    Map<String, String> response = new HashMap<>();
                    response.put("validationResponse", validationCode);

                    return request.createResponseBuilder(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(response)
                        .build();
                }

                if ("RolCreado".equals(eventType)) {
                    RolesDTO rol = mapper.treeToValue(event.get("data"), RolesDTO.class);

                    // 🔧 Aquí sigue tu lógica de base de datos (igual que antes)
                    WalletUtil.copyWalletToTemp(System.getProperty("java.io.tmpdir"), context);
                    String tmpDir = System.getProperty("java.io.tmpdir");
                    String walletPath = tmpDir.contains("\\") ? tmpDir.replace("\\", "/") : tmpDir;
                    String oracleUrl = "jdbc:oracle:thin:@et2xa97ns8rti1vt_tp?TNS_ADMIN=" + walletPath;
                    String oracleUser = "duoc_fullstack";
                    String oraclePass = "Eduardocr#2610";

                    try (Connection conn = DriverManager.getConnection(oracleUrl, oracleUser, oraclePass)) {
                        conn.setAutoCommit(false);

                        String sqlRol = "INSERT INTO ROLES (ROL) VALUES (?)";
                        PreparedStatement stmtRol = conn.prepareStatement(sqlRol, new String[]{"ID"});
                        stmtRol.setString(1, rol.getRol());
                        stmtRol.executeUpdate();

                        ResultSet rs = stmtRol.getGeneratedKeys();
                        Long nuevoRolId = rs.next() ? rs.getLong(1) : null;
                        if (nuevoRolId == null) {
                            conn.rollback();
                            context.getLogger().severe("No se pudo obtener ID del rol insertado.");
                            continue;
                        }

                        conn.commit();
                        context.getLogger().info("Rol creado con ID: " + nuevoRolId);
                    }
                }
            }

            return request.createResponseBuilder(HttpStatus.OK)
                .body("Evento procesado correctamente.")
                .build();

        } catch (Exception e) {
            context.getLogger().severe("Error: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error procesando el evento: " + e.getMessage())
                .build();
        }
    }
}
