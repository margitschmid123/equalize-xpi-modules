package com.equalize.xpi.af.modules;

import java.util.Iterator;

import com.equalize.xpi.af.modules.util.AbstractModule;
import com.equalize.xpi.af.modules.util.MessageDispatcher;
import com.sap.aii.af.lib.mp.module.ModuleException;
import com.sap.engine.interfaces.messaging.api.DeliverySemantics;
import com.sap.engine.interfaces.messaging.api.Payload;
import com.sap.engine.interfaces.messaging.api.auditlog.AuditLogStatus;

public class AttachmentSplitterBean extends AbstractModule {

	private String mode;
	private boolean keepAttachments;
	private MessageDispatcher msgdisp;	
	private String contentType;
	private String qualityOfService;

	@SuppressWarnings("unchecked")
	@Override
	protected void processModule() throws ModuleException {
		try {			
			// Module parameters
			this.mode = this.param.getMandatoryParameter("mode");
			this.param.checkParamValidValues("mode", "binding,channel");
			this.qualityOfService = this.param.getMandatoryParameter("qualityOfService");
			this.param.checkParamValidValues("qualityOfService", "EO,EOIO,BE");
			this.contentType = this.param.getParameter("contentType");
			this.keepAttachments = this.param.getBoolParameter("keepAttachments", "N", false);

			// Get attachments of the message
			Iterator<Payload> iter = this.msg.getAttachmentIterator();
			if(iter.hasNext()) {
				// Create message dispatcher
				if(this.mode.equals("binding")) {
					String adapterType = this.param.getConditionallyMandatoryParameter("adapterType", "mode", "binding");
					String adapterNS = this.param.getConditionallyMandatoryParameter("adapterNS", "mode", "binding");
					String fromParty = this.param.getParameter("fromParty", "", false);
					String fromService = this.param.getConditionallyMandatoryParameter("fromService", "mode", "binding");
					String toParty = this.param.getParameter("toParty", "", false);
					String toService = this.param.getParameter("toService", "", false);
					String interfaceName = this.param.getConditionallyMandatoryParameter("interfaceName", "mode", "binding");
					String interfaceNamespace = this.param.getConditionallyMandatoryParameter("interfaceNamespace", "mode", "binding");
					this.msgdisp = new MessageDispatcher(adapterType, adapterNS, fromParty, fromService, toParty, toService, interfaceName, interfaceNamespace, this.audit);
				} else if(this.mode.equals("channel")) {
					String channelID = this.param.getConditionallyMandatoryParameter("channelID", "mode", "channel");
					this.msgdisp = new MessageDispatcher(channelID, this.audit);					
				}
				
				// Iterate through the attachments and dispatch each as a child message
				while(iter.hasNext()) {
					Payload childPayload = iter.next();
					// Create child message and set reference message ID
					this.msgdisp.createMessage(childPayload.getContent(), getDeliverySemantics(this.qualityOfService));
					this.msgdisp.setRefToMessageId(this.msg.getMessageId());
					// Set child message content type
					if(this.contentType == null) {
						this.msgdisp.setPayloadContentType(childPayload.getContentType());
					} else {
						this.msgdisp.setPayloadContentType(this.contentType);
					}
					// Dispatch child message
					this.msgdisp.dispatchMessage();

					// Remove attachments from main payload
					if(!this.keepAttachments) {					
						String attachmentName = childPayload.getName();
						this.audit.addLog(AuditLogStatus.SUCCESS, "Removing attachment " + attachmentName + "from main payload");
						this.msg.removeAttachment(attachmentName);
					}
				}
			} else {
				// No attachments in message
				this.audit.addLog(AuditLogStatus.WARNING, "Message has no attachments to split");
			}
		} catch (Exception e) {
			throw new ModuleException(e.getMessage(), e);
		}
	}

	private DeliverySemantics getDeliverySemantics(String qos) {
		if(qos.equals("EO")) {
			return DeliverySemantics.ExactlyOnce;
		} else if (qos.equals("EOIO")) {
			return DeliverySemantics.ExactlyOnceInOrder;
		} else if (qos.equals("BE")) {
			return DeliverySemantics.BestEffort;
		}
		throw new RuntimeException("Invalid QoS: " + qos);
	}
}