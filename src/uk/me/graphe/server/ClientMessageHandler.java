package uk.me.graphe.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import uk.me.graphe.server.database.UserDatabase;
import uk.me.graphe.server.database.Database;
import uk.me.graphe.server.ot.GraphProcessor;
import uk.me.graphe.server.UserAuth;
import uk.me.graphe.shared.graphmanagers.OTGraphManager2d;
import uk.me.graphe.shared.jsonwrapper.JSONException;
import uk.me.graphe.shared.jsonwrapper.JSONImplHolder;
import uk.me.graphe.shared.jsonwrapper.JSONObject;
import uk.me.graphe.shared.messages.ChatMessage;
import uk.me.graphe.shared.messages.GraphListMessage;
import uk.me.graphe.shared.messages.UserAuthMessage;
import uk.me.graphe.shared.messages.AddPrivsMessage;
import uk.me.graphe.shared.messages.Message;
import uk.me.graphe.shared.messages.MessageFactory;
import uk.me.graphe.shared.messages.NoSuchGraphMessage;
import uk.me.graphe.shared.messages.OpenGraphMessage;
import uk.me.graphe.shared.messages.RequestGraphMessage;
import uk.me.graphe.shared.messages.SetGraphPropertiesMessage;
import uk.me.graphe.shared.messages.SetNameForIdMessage;
import uk.me.graphe.shared.messages.StateIdMessage;
import uk.me.graphe.shared.messages.factories.SetNameForIdFactory;
import uk.me.graphe.shared.messages.operations.CompositeOperation;
import uk.me.graphe.shared.messages.operations.GraphOperation;

/**
 * reads messages from clients, and validates them. Sends client message pairs
 * to the message processor for transformation
 * 
 * @author Sam Phippen <samphippen@googlemail.com>
 * 
 */
public class ClientMessageHandler extends Thread {

    private ClientManager mClientManager = ClientManager.getInstance();
    private boolean mShutDown = false;
    private HeartbeatManager mHbm = new HeartbeatManager();
    private GraphProcessor mProcessor = GraphProcessor.getInstance();
    private static ClientMessageHandler sInstance = null;
    private static UserAuth userAuth = new UserAuth();
    private static UserDatabase mUserDatabase = new UserDatabase();

    public ClientMessageHandler() {}

    @Override
    public void run() {

        while (!mShutDown) {
            Set<Client> availableClients = mClientManager
                    .waitOnReadableClients();
            for (Client c : availableClients) {
                List<String> messages = c.readNextMessages();
                if (messages != null)
                    System.err.println("len messages:" + messages.size());
                // if this returns null we disconnect the client for sending bad
                // messages
                List<JSONObject> jsos = validateAndParse(messages);

                if (jsos == null) {
                    System.err.println("disconnecting");
                    mClientManager.disconnect(c);
                } else {
                    processRequest(c, jsos);
                }

            }

        }

    }

    private void processRequest(Client c, List<JSONObject> jsos) throws Error {
        List<Message> ops;
        // malformed json == disconect
        try {
            ops = MessageFactory.makeOperationsFromJson(jsos);
            for (Message message : ops) {
                handleMessage(c, message);
            }
        } catch (JSONException e) {
            mClientManager.disconnect(c);
        } catch (InterruptedException e) {
            return;
        }
    }

    private void handleMessage(Client c, Message message)
            throws InterruptedException, Error {
        if (message.getMessage().equals("heartbeat")) {
            mHbm.beatWhenPossible(c);
        } else if (message.getMessage().equals("makeGraph")) {
            int id = DataManager.create();
            c.setCurrentGraphId(id);
            ClientMessageSender.getInstance().sendMessage(c,
                    new OpenGraphMessage(id));

            int stateId = DataManager.getGraph(id).getStateId();
            ClientMessageSender.getInstance().sendMessage(c,
                    new CompositeOperation(new ArrayList<GraphOperation>()));
            ClientMessageSender.getInstance().sendMessage(c,
                    new StateIdMessage(id, stateId));
            c.updateStateId(stateId);
        } else if (message.getMessage().equals("requestGraph")) {
            System.err.println("got rgm");
            RequestGraphMessage rgm = (RequestGraphMessage) message;
            OTGraphManager2d g = DataManager.getGraph(rgm.getGraphId());
            c.setCurrentGraphId(rgm.getGraphId());
            if (g == null) {
                ClientMessageSender.getInstance().sendMessage(c,
                        new NoSuchGraphMessage());
                System.err.println("no such graph as the one requested");
            } else {
                CompositeOperation delta = g.getOperationDelta(rgm.getSince());
                ClientMessageSender.getInstance().sendMessage(c, delta);
                ClientMessageSender.getInstance().sendMessage(c,
                        new StateIdMessage(rgm.getGraphId(), g.getStateId()));
                c.updateStateId(rgm.getSince());
            }
        } else if (message.getMessage().equals("chat")){
             System.err.println("got cm");
            ChatMessage cm = (ChatMessage) message;
            cm.setUserId(mUserDatabase.getEmailFromId(c.getUserId()));
            for (Client otherClients : ClientManager.getInstance()
                    .clientsForGraph(c.getCurrentGraphId())) {
                if (c != otherClients)
                    ClientMessageSender.getInstance().sendMessage(otherClients,
                            cm);
            }
        } else if (message.getMessage().equals("userAuth")){
        	System.err.println("got auth request from client");
        	UserAuthMessage uam = (UserAuthMessage) message;
        	
        	String ak = uam.getAuthKey();
        	
        	if((ak.length() < 10) || (ak == null) || (ak == "") || (ak.isEmpty())){
        		//if step 1, do discovery
        		// discover provider for this openid url
            	uam = userAuth.authenticateOpenId(uam.getOpUrl());
            	// send url to client for redirection
            	System.err.println("redir url is:" + uam.getRedirectionUrl());
            	ClientMessageSender.getInstance().sendMessage(c, uam);
        	}else{
        		//if step 2, verify
        		
        		//if provider didn't provide e-mail address and we don't have it in the db
        	    //already, request this from the user
    			if((uam.getEmailAddress().length() < 5) && (mUserDatabase.exists(uam.getId(), uam.getEmailAddress()))){
                    System.err.println("no email address retrieved from openid provider :(");
    				uam.setEmailAddress("need");
    				ClientMessageSender.getInstance().sendMessage(c, uam);
    				return;
    			}
        		
        		System.err.println("verifying openid");
        		if(userAuth.verifyOpenId(uam)){

        			System.err.println("oid verification successful");

        			//create user if it doesn't exist
        			if(!mUserDatabase.exists(uam.getId(), uam.getEmailAddress())){
        				mUserDatabase.newUser(uam.getId(), uam.getEmailAddress());
        				//TODO: remove this is it's for testing
        				mUserDatabase.addGraph(uam.getId(), "1");
        			}
        			
        			String balls[] = mUserDatabase.getUserIDs();
        			for(int i = 0; i < balls.length; i++){
        			System.err.println("user " +  balls[i]);
        			}
        			
        			c.setUserId(uam.getId());
        			
        			//send auth message to client
        			uam.setAuthd(true);
        			
        			ClientMessageSender.getInstance().sendMessage(c, uam);
        			
        		}else{
        			System.err.println("oid verification failed");
        			uam.setAuthd(false);
                    ClientMessageSender.getInstance().sendMessage(c, uam);
        		}
        		
        	}
        	
        } else if (message.getMessage().equals("graphList")){
            //get list of ids for this user
            try{
                List<String> ids = mUserDatabase.getGraphs(c.getUserId());
                List<String> names = new ArrayList<String> ();
                //get name of each graph
                for (int i = 0; i < ids.size(); i++){
                    System.err.println(DataManager.getGraphName(Integer.parseInt(ids.get(i))));
                    names.add(DataManager.getGraphName(Integer.parseInt(ids.get(i))));
                }
    
            	GraphListMessage glm = new GraphListMessage(ids.toString(), names.toString());
            	ClientMessageSender.getInstance().sendMessage(c, glm);
            	
            }catch(NullPointerException e){
                GraphListMessage glm = new GraphListMessage("","");
                ClientMessageSender.getInstance().sendMessage(c, glm);
            }
        } else if (message.getMessage().equals("sgp")) {
            SetGraphPropertiesMessage sgpm = (SetGraphPropertiesMessage) message;
            DataManager.setGraphProperties(c.getCurrentGraphId(), sgpm);
            for (Client co : ClientManager.getInstance().clientsForGraph(
                    c.getCurrentGraphId())) {
                ClientMessageSender.getInstance().sendMessage(co, sgpm);
            }
        } else if (message.getMessage().equals("setNameForId")) {
            SetNameForIdMessage snfi = (SetNameForIdMessage) message;
            DataManager.renameGraph(c.getCurrentGraphId(), snfi.getTitle());
            List<Client> clients = ClientManager.getInstance().clientsForGraph(
                    c.getCurrentGraphId());
            for (Client cOut : clients) {
                ClientMessageSender.getInstance().sendMessage(cOut, snfi);
            }
        } else if (message.getMessage().equals("addPrivs")){
            AddPrivsMessage apm = (AddPrivsMessage) message;
            
            if (apm.getGraphId().length() < 5){
                mUserDatabase.setGraphsToUsers(apm.getEmailAddress(),
                        Integer.toString(c.getCurrentGraphId()));
            } else {
                mUserDatabase.setGraphsToUsers(apm.getEmailAddress(),c.getUserId());
            }
        } else if (message.getMessage().equals("logout")){
            c.setUserId("");
        } else if (message.isOperation()) {
            mProcessor.submit(c, (GraphOperation) message);
        } else {
            throw new Error("got unexpected message from client");
        }
    }

    private List<JSONObject> validateAndParse(List<String> messages) {
        List<JSONObject> result = new ArrayList<JSONObject>();
        if (messages == null) return null;
        for (String s : messages) {
            try {
                System.err.println(s);
                JSONObject o = JSONImplHolder.make(s);
                result.add(o);
            } catch (JSONException e) {
                return null;
            }

        }

        return result;

    }

    public static ClientMessageHandler getInstance() {
        if (sInstance == null) sInstance = new ClientMessageHandler();
        return sInstance;
    }

    public void shutDown() {
        mShutDown = true;
        this.interrupt();
        mClientManager.wakeUp();
    }

}
