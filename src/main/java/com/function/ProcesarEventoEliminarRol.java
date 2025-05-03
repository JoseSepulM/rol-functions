package com.function;

import java.sql.*;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.function.util.WalletUtil;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

public class ProcesarEventoEliminarRol {

    @FunctionName("ProcesarEventoEliminarRol")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS, route = "event/rol/eliminar") HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Procesando evento de eliminación de rol");

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode[] events = mapper.readValue(request.getBody().orElse("[]"), JsonNode[].class);

            // 🔍 Validación de suscripción (se maneja al inicio y corta el flujo con
            // return)
            JsonNode firstEvent = events[0];
            String eventType = firstEvent.get("eventType").asText();
            if ("Microsoft.EventGrid.SubscriptionValidationEvent".equals(eventType)) {
                String validationCode = firstEvent.get("data").get("validationCode").asText();
                Map<String, String> response = new HashMap<>();
                response.put("validationResponse", validationCode);
                return request.createResponseBuilder(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(response)
                        .build();
            }

            for (JsonNode event : events) {
                if ("RolEliminado".equals(event.get("eventType").asText())) {
                    String subject = event.get("subject").asText();
                    String id = subject.substring(subject.lastIndexOf("/") + 1);

                    WalletUtil.copyWalletToTemp(System.getProperty("java.io.tmpdir"), context);
                    String tmpDir = System.getProperty("java.io.tmpdir").replace("\\", "/");
                    String oracleUrl = "jdbc:oracle:thin:@et2xa97ns8rti1vt_tp?TNS_ADMIN=" + tmpDir;
                    String oracleUser = "duoc_fullstack";
                    String oraclePass = "Eduardocr#2610";

                    try (Connection conn = DriverManager.getConnection(oracleUrl, oracleUser, oraclePass)) {
                        conn.setAutoCommit(false);
                        PreparedStatement stmtRol = conn.prepareStatement("DELETE FROM ROLES WHERE ID = ?");
                        stmtRol.setString(1, id);
                        int filas = stmtRol.executeUpdate();

                        if (filas > 0) {
                            conn.commit();
                            context.getLogger().info("Rol eliminado: ID " + id);
                        } else {
                            conn.rollback();
                            context.getLogger().warning("No se encontró Rol con ID: " + id);
                        }
                    }
                }
            }

            return request.createResponseBuilder(HttpStatus.OK).body("Evento de eliminación procesado.").build();

        } catch (Exception e) {
            context.getLogger().severe("Error procesando evento: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage()).build();
        }
    }
}