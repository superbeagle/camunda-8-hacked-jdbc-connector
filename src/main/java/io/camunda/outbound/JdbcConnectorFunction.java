package io.camunda.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.outbound.db.DatabaseManager;
import io.camunda.outbound.params.CommandParams;
import io.camunda.outbound.params.JDBCParams;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

//import static io.camunda.connector.JdbcConnectorRequest.*;

@OutboundConnector(
    name = "JDBC",
    inputVariables = {"jdbc", "command"},
    type = "io.camunda:connector-jdbc:1")
@ElementTemplate(
        id="io.camunda.outbound.Jdbc",
        name="JDBC connector",
        inputDataClass = JdbcConnectorRequest.class

)
public class JdbcConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(JdbcConnectorFunction.class);

  Map<JDBCParams, DatabaseManager> databaseManagers = new HashMap<>();

  @Override
  public Object execute(OutboundConnectorContext context) {
    var connectorRequest = context.getJobContext().getVariables();
    //var connectorRequest = context.bindVariables(JdbcConnectorRequest.class);
    //context.validate(connectorRequest);
    //context.replaceSecrets(connectorRequest);

    return executeConnector(connectorRequest);
  }

  private Object executeConnector(final String connectorRequest) {

    LOGGER.info("Executing my connector with request {}", connectorRequest);

    JSONObject jsonObject = new JSONObject(connectorRequest);

    JDBCParams jdbcParms = new JDBCParams();
    jdbcParms.setJdbcUrl(jsonObject.getJSONObject("jdbc").getString("jdbcUrl"));
    jdbcParms.setUserName(jsonObject.getJSONObject("jdbc").getString("userName"));
    jdbcParms.setPassword(jsonObject.getJSONObject("jdbc").getString("password"));
    DatabaseManager db = databaseManagers.get(jdbcParms);

    if(db == null) {
      db = new DatabaseManager(jdbcParms);
      databaseManagers.put(jdbcParms, db);
    }

    CommandParams command = new CommandParams();
    command.setCommandType(jsonObject.getJSONObject("command").getString("commandType"));
    command.setSql(jsonObject.getJSONObject("command").getString("sql"));

    ObjectMapper mapper = new ObjectMapper();
    try {
      Map<String, Object> rawMap = mapper.readValue(jsonObject.getJSONObject("command").getJSONObject("params").toString(), Map.class);
      Map<Integer, Object> map = new HashMap<>();
      for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
        try {
          map.put(Integer.parseInt(entry.getKey()), entry.getValue());
        } catch (ClassCastException e) {
          throw e; // or a required error handling
        }
      }
      command.setParams(map);
    } catch(IOException e) {
      LOGGER.error("Mapping error: "+e);
    }

    if(command.getCommandType().equals("selectOne")) {
      return db.selectOne(command.getSql(), command.getParams());
    } else if(command.getCommandType().equals("selectList")) {
      return db.selectList(command.getSql(), command.getParams());
    } else if(command.getCommandType().equals("selectMap")) {
      return db.selectMap(command.getSql(), command.getParams(), command.getMapKey());
    } else if(command.getCommandType().equals("insert")) {
      return db.update(command.getSql(), command.getParams());
    } else if(command.getCommandType().equals("update")) {
      return db.update(command.getSql(), command.getParams());
    } else if(command.getCommandType().equals("delete")) {
      return db.update(command.getSql(), command.getParams());
    } else {
      throw new UnsupportedOperationException("The command type" + command.getCommandType() + " is not currently supported");
    }
  }

}
