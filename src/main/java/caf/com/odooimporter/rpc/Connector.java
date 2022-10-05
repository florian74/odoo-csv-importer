package caf.com.odooimporter.rpc;

import com.odoojava.api.OdooXmlRpcProxy;
import com.odoojava.api.Session;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

//rpc java service
@Getter
@Service
@Slf4j
public class Connector {
    
    private Session session;
    
    private Boolean isOk;
    
    public Connector(@Value("${odoo.host}") String host, @Value("${odoo.port}") Integer port, @Value("${odoo.database}") String database,
                     @Value("${odoo.login}") String login, @Value("${odoo.password}") String password ) {
        log.info("start odoo session at " + host + ":" + port);
        session = new Session(
                OdooXmlRpcProxy.RPCProtocol.RPC_HTTP,
                host,
                port,
                database,
                login,
                password
        );

        try {
            session.startSession();
            log.info("session started with odoo version: " + session.getServerVersion().toString());
            this.isOk = true;
        } catch (Exception e) {
            log.error("cannot start session", e);
        }
    }
    
}
