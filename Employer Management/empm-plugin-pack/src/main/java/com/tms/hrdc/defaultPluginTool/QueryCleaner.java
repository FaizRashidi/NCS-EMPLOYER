/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tms.hrdc.defaultPluginTool;

import static com.tms.hrdc.defaultPluginTool.SchedulerQueryReminder.url_contextPath;
import static com.tms.hrdc.defaultPluginTool.SchedulerQueryReminder.url_scheme;
import static com.tms.hrdc.defaultPluginTool.SchedulerQueryReminder.url_serverName;
import static com.tms.hrdc.defaultPluginTool.SchedulerQueryReminder.url_serverPort;
import com.tms.hrdc.util.DBHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;

/**
 *
 * @author faizr
 */
public class QueryCleaner extends DefaultApplicationPlugin{

    @Override
    public String getName() {
        return this.getClass().toString();
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "To Kill Any Unresponded Queries After Approve/Reject";
    }

    @Override
    public String getLabel() {
        return "HRDC - EMPM - Query Kill - Maintenance Tool";
    }

    @Override
    public String getClassName() {
        return this.getClass().toString();
    }

    @Override
    public String getPropertyOptions() {
        return "";
    }
    
    private void msg(String msg){
        LogUtil.info(this.getClassName(), msg);
    }
    
    PluginManager pluginManager = null;
    WorkflowManager workflowManager = null;
    WorkflowAssignment wfAssignment = null;
    
    @Override
    public Object execute(Map props) {
        DBHandler db = new DBHandler();
        
        wfAssignment = (WorkflowAssignment) props.get("workflowAssignment");             
        pluginManager = (PluginManager) props.get("pluginManager");
        workflowManager = (WorkflowManager) pluginManager.getBean("workflowManager"); 
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        
        String id = appService.getOriginProcessId(wfAssignment.getProcessId());
        
        String status_form4 = workflowManager.getProcessVariable(wfAssignment.getProcessId(), "status_form4");
        String status = workflowManager.getProcessVariable(wfAssignment.getProcessId(), "status");
        
        msg("Status wfv "+status+", status form 4 "+status_form4+" processID "+wfAssignment.getProcessId());
        
        //check if got query
        try{
            db.openConnection();
            
            ArrayList<HashMap<String,String>> standingList = db.select(
                    "select distinct ActivityId, act.ActivityDefinitionId from SHKAssignmentsTable a \n" +
                    "inner join SHKActivities act ON act.id = a.ActivityId\n" + 
                   "where ActivityProcessId = ? \n" +
                    "and ActivityProcessId like '%empm%'\n" +
                    "and (ActivityId like '%query%' "
                    + "OR ActivityId like '%reprocess%' "
                    + "OR ActivityId like '%form4_2%'"
                    + "OR ActivityId like '%form4_m_2%')",
                    new String[]{wfAssignment.getProcessId()}
            );
            
            if(standingList.isEmpty()){
                msg("NO UNRESPONDED QUERY ABORTED ON APPROVAL/REJECTION");
            }
            
            for(HashMap hm:standingList){
                String actID = hm.getOrDefault("ActivityId", "").toString();
                String actDefID = hm.getOrDefault("ActivityDefinitionId", "").toString();
                
                if(actID.endsWith("query")){
                    msg("UNRESPONDED QUERY "+actID+" ABORTED ON APPROVAL/REJECTION");
                    workflowManager.activityAbort(wfAssignment.getProcessId(), actDefID);
                }
                
                if(!actID.endsWith("query") && (status.equals("Approved") || status_form4.contains("approved"))){
                    msg("APPROVAL"+actID+" ABORTED ON TIMEOUT");
                    workflowManager.activityAbort(wfAssignment.getProcessId(), actDefID);
                }
            }
            
            
            
            
        }catch(Exception e){
            e.printStackTrace();
        }
        
        return null;
    }
}
