/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tms.hrdc.defaultPluginTool;

import com.tms.hrdc.dao.CurrentUser;
import com.tms.hrdc.util.Constants;
import com.tms.hrdc.util.DBHandler;
import com.tms.hrdc.webservice.models.AssignmentsImpl;
import java.sql.SQLException;
import java.util.ArrayList;
import com.tms.hrdc.dao.Process;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
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
public class TaskAssignerTool extends DefaultApplicationPlugin{

    @Override
    public String getName() {
        return this.getClassName();
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "To assign task to whoever is appointed from the setup";
    }

    @Override
    public String getLabel() {
        return "HRDC - EMPM - Task Assigner Tool";
    }

    @Override
    public String getClassName() {
        return this.getClass().toString();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/task_assigner_tool.json", null, true);
    }
    
    private void msg(String msg){
        LogUtil.info(this.getClassName(), msg);
    }
    
    PluginManager pluginManager = null;
    WorkflowManager workflowManager = null;
    WorkflowAssignment wfAssignment = null;

    String MYCOID = "", STATE = "", CITY = "", IND = "", IND_CODE = "", 
            SETUP_TYPE = "", USERID = "",  SUBMITBYNAME = "";
    
    @Override
    public Object execute(Map props) {
        
        String app_type = (String) props.get("app_type");
        
        wfAssignment = (WorkflowAssignment) props.get("workflowAssignment");     
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        String id = appService.getOriginProcessId(wfAssignment.getProcessId());
        String empId = "";
        pluginManager = (PluginManager) props.get("pluginManager");
        workflowManager = (WorkflowManager) pluginManager.getBean("workflowManager"); 
        
        DBHandler db = new DBHandler();
        try {
            db.openConnection();
            
            Process proc;
            proc = new Process(db, app_type, id);
            
            if(app_type.equals(Process.TYPE_E_DEREG)){
                String isForm5Submitted = workflowManager.getProcessVariable(wfAssignment.getProcessId(), "require_form5");
                
                if(StringUtils.isBlank(isForm5Submitted)){
                    proc = new Process(db, app_type, id);
                }else{
                    proc = new Process(db, Process.TYPE_E_DEREG_F5, id);
                }
            }            
                        
            switch(proc.getProcessType()){
                case Process.TYPE_E_EMPREG:
                    setERegApprovers(props, proc); 
                break;
                case Process.TYPE_E_DEREG:
                    setDeregApprovers(props, proc); 
                break;                
                case Process.TYPE_E_DEREG_F5:
                    setDeregApprovers(props, proc); 
                break;
                case Process.TYPE_E_DEREG_WD:
                    setWDDeregApprovers(props, proc); 
                break;
                case Process.TYPE_E_DEREG_CANCEL:
                    setCancelDeregApprovers(props, proc); 
                break;
                case Process.TYPE_PE_ENGAGEMENT:
                    setEgmntApprovers(props, proc); 
                break;
                case Process.TYPE_E_REQ_CHANGE:
                    setReqChangeApprovers(props, proc); 
                break;
            }
            
            db.closeConnection();
            
        } catch (SQLException ex) {
            Logger.getLogger(TaskAssignerTool.class.getName()).log(Level.SEVERE, null, ex);
        } finally{
            db.closeConnection();
        }       
        
        return null;
    }    
    
    private String setApprovers(String type, Process proc) {
        String query = "";
        String return_ids = "";
        ArrayList<HashMap<String, String>> data = null;
        
        String dist_type = proc.getSetupData().getOrDefault(Process.DISTRIBUTION, "General").toString();
        String rr_field_name = proc.getSetupData().getOrDefault(Process.CAT_FIELD, "").toString();
        String categ_val = proc.getSetupData().getOrDefault(Process.CAT_VALUE, "").toString();
        String stp_id = proc.getSetupData().getOrDefault(Process.SETUP_ID, "").toString();
        
        DBHandler db = proc.getDBHandler();
        
        switch(type){
            case "supervisor":
                query = "SELECT c_usr, '' as name, c_roundRobin_count, 'c_sv_fk' as fk "
                        + "FROM app_fd_empm_appvr_usr_stp WHERE c_sv_fk = ? "
                        + "AND  c_status = 'Active' ";
                break;
                   
            case "staff":
                query = "SELECT c_usr, '' as name, c_roundRobin_count, 'c_staff_fk' as fk "
                        + "FROM app_fd_empm_appvr_usr_stp WHERE c_staff_fk = ? "
                        + "AND c_status = 'Active' ";
                
            break;
            case "officer":
                query = "SELECT c_usr, '' as name, c_roundRobin_count, 'c_officer_fk' as fk "
                        + " FROM app_fd_empm_appvr_usr_stp WHERE c_officer_fk = ? "
                        + "AND c_status = 'Active' ";
            break;
            case "approver":
                query = "SELECT c_usr, '' as name, c_roundRobin_count, 'c_approver_fk' as fk "
                        + " FROM app_fd_empm_appvr_usr_stp WHERE c_approver_fk = ? "
                        + "AND c_status = 'Active' ";
            break;
            case "erdAdmin":
                query = "SELECT c_usr, '' as name, c_roundRobin_count, 'c_erd_admin_fk' as fk "
                        + " FROM app_fd_empm_appvr_usr_stp WHERE c_erd_admin_fk = ? "
                        + "AND c_status = 'Active'  ";
            break;
            case "erdAdmin2":
                query = "SELECT c_usr, '' as name, c_roundRobin_count, 'c_erd_admin_2_fk' as fk "
                        + " FROM app_fd_empm_appvr_usr_stp WHERE c_erd_admin_2_fk = ?  "
                        + "AND c_status = 'Active' ";
            break;
            case "egmntOfficer":
                query = "SELECT c_usr, '' as name, c_roundRobin_count, 'c_eng_officer_fk' as fk "
                        + " FROM app_fd_empm_appvr_usr_stp WHERE c_eng_officer_fk = ? "
                        + "AND c_status = 'Active'";
                type = "officer";
            break;
            case "peOfficer":
                query = "SELECT c_usr, '' as name, c_roundRobin_count, 'c_officer_fk' as fk "
                        + " FROM app_fd_empm_appvr_usr_stp WHERE c_officer_fk = ? "
                        + "AND c_status = 'Active' ";
                type = "approver";
            break;
        }
        
//        msg("type, setup id, rr cat: "+stp_id+", "+categ_val+", "+rr_field_name
//        +"Finding "+type+" Type "+dist_type);
        
        switch(dist_type){
            case "By City":
            case "By State":
            case "By Industry":
            case "By Sector":
                query+= " AND "+rr_field_name + " = ?  ORDER BY c_usr ASC ";
                data = db.select(query, new String[]{stp_id,categ_val});     
            break;
            case "General":
                query+= " ORDER BY c_usr ASC ";
                data = db.select(query, new String[]{stp_id});
            break;
            case "Manual Distribution":
                data = proc.getSV();
            break;
        }
        
        data = doRoundRobin(db, type, stp_id, data);
        
        if((data == null || data.size() == 0)){ // if nobody found for that RR categ, assign to all
            
            data = db.select(query, new String[]{stp_id});               
            msg("Found no "+type+", Querying for all,  found - "+(data==null?"":data.toString() ));
        }
        
        AssignmentsImpl.recordAssignedOfficers(db, proc.getProcessStarter(),data, proc.getRecId(), type);
        
        if(!rr_field_name.equals("Manual Distribution")){//include sv so they can see
            ArrayList<HashMap<String, String>> svList = proc.getSV();
            
            if(data == null || data.size() == 0){
                data = svList;
            }else{
                data.addAll(svList);
            }
            
            msg( "including svs ======> "+svList);
        }
                
        ArrayList aprvrList = new ArrayList();
        for(HashMap hm:data){     
            
            if(!aprvrList.contains(hm.get("c_usr").toString()) ){
                aprvrList.add(hm.get("c_usr").toString());
            }
        }        
        
        return_ids = String.join(Constants.SEPARATOR, aprvrList);
        
        msg("Approver Settings: "
                +dist_type
                +" - "+type+". Users: "+return_ids);
        
        return return_ids;
    }
    
    public String getUserName(DBHandler db, String usrId){
        String query = "SELECT c_username FROM app_fd_stp_hrdc_usr WHERE id = ?";
        return db.selectOneValueFromTable(query, new String[]{usrId});
    }    
    

    private HashMap getDeregData(DBHandler db, String id) {
        String sql = "select c_dreg_emp_id, \n" +
                        "(select GROUP_CONCAT(d.username SEPARATOR ';') from dir_user d  \n" +
                        "inner join app_fd_empm_usermap u on u.c_userId = d.id \n" +
                        "where u.c_compId = dr.c_dreg_emp_id limit 1) as deregEmpId, \n" +
                
                        "(select GROUP_CONCAT(d.username SEPARATOR ';') from dir_user d  \n" +
                        "inner join app_fd_empm_usermap u on u.c_userId = d.id \n" +
                        "where u.c_compId = dr.c_merge_comp_id) as mergerEmpId, \n" +
                
                        "case \n" +
                        "when c_submitting_name = 'other'\n" +
                        "then c_otherPerson\n" +
                        "else (select c_name from app_fd_empm_cntct_oth where id = dr.c_submitting_name)\n" +
                        "end as c_submitting_name, c_submit_mode, createdByName \n" +
                
                        "from \n" +
                        "app_fd_empm_dereg dr  \n" +
                        "where id = ?";
        HashMap hm = db.selectOneRecord(sql, new String[]{id});
        return hm;
    }

    /*
    list the guys in order
    do calculation based on past selected number and get the position of the guy in the list
    update the selected number
    */
    private ArrayList<HashMap<String, String>> doRoundRobin(DBHandler db, String type, String stp_id, ArrayList<HashMap<String, String>> data) {
        
        if(data.isEmpty()){
            return data;
        }
        
        String fk = StringUtils.isBlank(data.get(0).get("fk"))?
                            "":
                            data.get(0).get("fk");
        String rr_count = StringUtils.isBlank(data.get(0).get("c_roundRobin_count"))?
                            "0":
                            data.get(0).get("c_roundRobin_count");
        int rr_count_int = Integer.parseInt(rr_count);
        int rr_pick = rr_count_int%data.size();
        
        HashMap selectedGuy = data.get(rr_pick);
        data.clear();
        data.add(selectedGuy);
        
        String query = "UPDATE "+Constants.TABLE.STP_APPROVER_USERS+" "
                + "SET c_roundRobin_count = '"+Integer.toString(++rr_pick)+"' "
                + "WHERE "+fk+" = '"+stp_id+"'";
        
        int i = db.update(query);
        
        return data;
    }

    private void setERegApprovers(Map props, Process proc) {
        
        String userStart = "", userIds = "", submitting_officer = "",
                req_email = "", form_type = "", submit_by = "", submit_mode = "";
        
//        HashMap hm = proc.getDBHandler().selectOneRecord(
//                "SELECT username FROM app_fd_empm_usermap u "
//                + "inner join dir_user d ON d.id = u.c_userId  "
//                + "where c_compId = ?",
//                new String[]{proc.getEmpObj().getId()}
//        );

        HashMap regApplData = proc.getDBHandler().selectOneRecord(
                "SELECT \n" +
                    "a.c_submit_mode, a.dateCreated,  a.c_form_type,\n" +
                    "a.createdBy,\n" +
                    "case \n" +
                    "when c_req_email is null || c_req_email = ''\n" +
                    "then r.c_empl_email_pri\n" +
                    "else c_req_email\n" +
                    "end as c_req_email,\n" +
                    "case \n" +
                    "when a.c_form_type = '1A'\n" +
                    "then a.createdByName\n" +
                    "when c_so_name is null || c_so_name = ''\n" +
                    "then concat(r.createdByName, ' (On Behalf)')\n" +
                    "else concat(r.c_so_name, ' (', r.c_empl_email_pri, ')' )\n" +
                    "end as pic\n" +
                    "from app_fd_empm_regAppl a \n" +
                    "INNER JOIN app_fd_empm_reg r on r.id = a.c_empl_fk " +
                    "WHERE a.id = ? ",
                new String[]{proc.getRecId()}
        );

        if(regApplData != null){
            req_email = regApplData.getOrDefault("c_req_email", "").toString();
            submitting_officer = regApplData.getOrDefault("pic", "").toString();
            form_type = regApplData.getOrDefault("c_form_type", "").toString();
            submit_by = regApplData.getOrDefault("createdBy", "").toString();
            submit_mode = regApplData.getOrDefault("c_submit_mode", "").toString();
        }

        userStart = req_email;

        //on behalf
        if(submit_mode.equals("OFFLINE") || submit_mode.equals("MANUAL")){
//            submitting_officer = new CurrentUser(submit_by).getFullName();
        }

        if(form_type.equals("1A")){
            userStart = submit_by;
            submitting_officer = new CurrentUser(submit_by).getFullName();
        }

        proc.setProcessStarter(submitting_officer);

        msg("REQUESTER PT - "+userStart);
        
        //if on behalf/f1A
        if(!StringUtils.isBlank(proc.getEmpObj().getField("c_asgn_officer"))){            
            String asgnOfficer = proc.getEmpObj().getField("c_asgn_officer");

            AssignmentsImpl.recordAssignedOfficers(asgnOfficer,
                    submitting_officer, proc.getRecId(), "officer", true);

            for(HashMap hm2:proc.getSV()){
                asgnOfficer+=";"+(hm2.get("c_usr")==null?"":hm2.get("c_usr").toString());
            }
            workflowManager.activityVariable(wfAssignment.getActivityId(), (String) props.get("officer_id"), asgnOfficer);
        }else{
            if(!StringUtils.isBlank((String) props.get("officer_id"))){
                userIds = setApprovers("officer", proc);
                workflowManager.activityVariable(wfAssignment.getActivityId(), (String) props.get("officer_id"), userIds);
            }
            
        }
        
        if(!StringUtils.isBlank((String) props.get("approver_id"))){
            userIds = setApprovers("approver", proc);
            workflowManager.activityVariable(wfAssignment.getActivityId(), (String) props.get("approver_id"), userIds);
        }

        if(!userStart.isEmpty()){
            workflowManager.activityVariable(wfAssignment.getActivityId(), (String) props.get("req_id"), userStart);
        }
    }

    private void setDeregApprovers(Map props, Process proc) {
        //for form 5 submitter (Deregistration)
        HashMap deregHm = getDeregData(proc.getDBHandler() , proc.getRecId());
        
        //form type online - submitting name (company
//         "c_submitting_name, c_submit_mode, createdByName \n" +
        String form_type = deregHm!=null?deregHm.getOrDefault("c_submit_mode", "ONLINE").toString():"ONLINE";        
        String createdByName = deregHm!=null?deregHm.getOrDefault("createdByName", "").toString():"";
        String submitting_name = deregHm!=null?deregHm.getOrDefault("c_submitting_name", createdByName).toString():createdByName;
        
        if(!form_type.equals("ONLINE")){
            proc.setProcessStarter(createdByName+" (On Behalf)");
        }else{
            proc.setProcessStarter(submitting_name);
        }        
        
        String userIds = "";
        
        if(deregHm!=null && deregHm.get("mergerEmpId")!=null){
            String mergerId = deregHm.get("mergerEmpId").toString();
            workflowManager.activityVariable(wfAssignment.getActivityId(), (String) props.get("req_f5_id"), mergerId);
        }       
        
        if(!StringUtils.isBlank((String) props.get("officer_id"))){
            userIds = setApprovers("officer", proc);
            workflowManager.activityVariable(wfAssignment.getActivityId(), (String) props.get("officer_id"), userIds);            
        }
        
        if(!StringUtils.isBlank((String) props.get("approver_id"))){
            proc.setProcessStarter(userIds);
            userIds = setApprovers("approver", proc);
            workflowManager.activityVariable(wfAssignment.getActivityId(), (String) props.get("approver_id"), userIds);
        }        
                
        workflowManager.activityVariable(wfAssignment.getActivityId(), (String) props.get("req_id"), deregHm.get("deregEmpId").toString()); 
    }

    private void setEgmntApprovers(Map props, Process proc) {
        String startUser = workflowManager.getProcessVariable(wfAssignment.getProcessId(), "reqId");
        String userIds = "";
        proc.setProcessStarter(startUser);
        
        if(!StringUtils.isBlank((String) props.get("officer_id"))){
            userIds = setApprovers("egmntOfficer", proc);
            workflowManager.activityVariable(wfAssignment.getActivityId(), (String) props.get("officer_id"), userIds);
        }
        if(!StringUtils.isBlank((String) props.get("approver_id"))){
            
            proc = new Process(proc.getDBHandler(), Process.TYPE_PE_EMG_ACKNOWLEDGE, proc.getRecId());
            userIds = setApprovers("peOfficer", proc);
            workflowManager.activityVariable(wfAssignment.getActivityId(), (String) props.get("approver_id"), userIds);
        }
    }

    private void setWDDeregApprovers(Map props, Process proc) {
        
        ArrayList<HashMap<String, String>> reqList = new ArrayList();
        //get dereg WF Act ID 
        //get all pic under this id
        HashMap hm = proc.getDBHandler().selectOneRecord(
                "select r.id, r.c_dereg_id from app_fd_empm_dereg_wd r "
                        + "WHERE r.id = ?",
                new String[]{proc.getRecId()}
        );
        
        String deregId = "";
        
        if(hm!=null){
            deregId = hm.get("c_dereg_id").toString();
        }
        
        hm = proc.getDBHandler().selectOneRecord(
                "select distinct s.ActivityId, ActivityProcessId," +
                "GROUP_CONCAT(s.ResourceId SEPARATOR ';') as userIds " +
                "from app_fd_empm_dereg d\n" +
                "INNER JOIN wf_process_link w ON w.originProcessId = d.id\n" +
                "INNER JOIN SHKAssignmentsTable s ON s.ActivityProcessId = w.processId\n" +
                "WHERE d.id = ?",
                new String[]{deregId}
        );
        
        String currentDeregActId = "";
        String currentDeregProcId = "";
        String currentDeregApproverId = "";
        
        currentDeregActId = hm.getOrDefault("ActivityId", "").toString();
        currentDeregProcId = hm.getOrDefault("ActivityProcessId", "").toString();
        currentDeregApproverId = hm.getOrDefault("userIds", "").toString();
        
        if(currentDeregActId.isEmpty()){
            
            HashMap deregHm = proc.getDBHandler().selectOneRecord(
                    "SELECT id, c_dreg_emp_id FROM app_fd_empm_dereg where c_f5_fk = ?",
                    new String[]{deregId}
            );
            
//            LogUtil.info("DEREG WD ", "2 "+deregHm.toString());
            
            String deregEmpId = "";
            if(deregHm!=null){
                deregId = deregHm.getOrDefault("id", "").toString();
                deregEmpId = deregHm.getOrDefault("c_dreg_emp_id", "").toString();
            }
            
            hm = proc.getDBHandler().selectOneRecord(
                    "select distinct s.ActivityId, ActivityProcessId," +
                    "GROUP_CONCAT(s.ResourceId SEPARATOR ';') as userIds " +
                    "from app_fd_empm_dereg d\n" +
                    "INNER JOIN wf_process_link w ON w.originProcessId = d.id\n" +
                    "INNER JOIN SHKAssignmentsTable s ON s.ActivityProcessId = w.processId\n" +
                    "WHERE d.id = ?",
                    new String[]{deregId}
            );
            
//            LogUtil.info("DEREG WD ", "3 "+hm.toString());
            
            currentDeregActId = hm.get("ActivityId").toString();
            currentDeregProcId = hm.get("ActivityProcessId").toString();
            currentDeregApproverId = hm.get("userIds").toString();
            
            reqList = proc.getDBHandler().select(
                    "SELECT 	c_userId \n" +
                    "FROM 		app_fd_empm_usermap u \n" +
                    "INNER JOIN 	app_fd_empm_reg r ON r.id = u.c_compId\n" +
                    "WHERE 		r.id = ? ",
                    new String[]{deregEmpId}
            );
            
            String deregReqList = 
                reqList.stream()
                .map(map -> map.values().stream().collect(Collectors.joining(";")))
                .collect(Collectors.joining(";"));
            
//            LogUtil.info("DEREG WD ", "4 "+deregReqList);
            
            workflowManager.activityVariable(wfAssignment.getActivityId(), "wd_merger", deregReqList);
        }
        
        msg("DEREG SETTING dereg Id "+deregId+
                " dereg Act Id "+currentDeregActId+
                " dereg approver "+currentDeregApproverId);
        
        reqList = proc.getDBHandler().select(
                "SELECT 	c_userId \n" +
                "FROM 		app_fd_empm_usermap u \n" +
                "INNER JOIN 	app_fd_empm_reg r ON r.id = u.c_compId\n" +
                "WHERE 		r.id = ? ",
                new String[]{proc.getEmpObj().getId()}
        );
        
        String reqListStr = 
                reqList.stream()
                .map(map -> map.values().stream().collect(Collectors.joining(";")))
                .collect(Collectors.joining(";"));
        
        proc.setProcessStarter(reqListStr);
        
        String userIdString = setApprovers("erdAdmin", proc);
        workflowManager.activityVariable(wfAssignment.getActivityId(), (String) props.get("req_id"), reqListStr);
        workflowManager.activityVariable(wfAssignment.getActivityId(), (String) props.get("approver_id"), userIdString);  
        workflowManager.activityVariable(wfAssignment.getActivityId(), "wd_dereg_approverId", currentDeregApproverId);
    }

    private void setCancelDeregApprovers(Map props, Process proc) {
        
        ArrayList<HashMap<String, String>> reqList = proc.getDBHandler().select(
                "SELECT 	c_userId \n" +
                "FROM 		app_fd_empm_usermap u \n" +
                "INNER JOIN 	app_fd_empm_reg r ON r.id = u.c_compId\n" +
                "WHERE 		r.id = ? ",
                new String[]{proc.getEmpObj().getId()}
        );
        
        String erdAdmin = setApprovers("erdAdmin", proc);
        String erdAdmin2 = setApprovers("erdAdmin2", proc);
        workflowManager.activityVariable(wfAssignment.getActivityId(), (String) props.get("approver_id"), erdAdmin2);
        workflowManager.activityVariable(wfAssignment.getActivityId(), (String) props.get("officer_id"), erdAdmin);
    }

    private void setReqChangeApprovers(Map props, Process proc) {
        String userStart = "", userIds = "";
        
        HashMap hm = proc.getDBHandler().selectOneRecord(
                "SELECT GROUP_CONCAT(d.username SEPARATOR ';') as username FROM app_fd_empm_usermap u "
                + "inner join dir_user d ON d.id = u.c_userId  "
                + "where c_compId = ?", 
                new String[]{proc.getEmpObj().getId()}
        );

        if(hm != null){
            userStart = hm.get("username") == null?"": (String)hm.get("username");
        }
        
        workflowManager.activityVariable(wfAssignment.getActivityId(), (String) props.get("req_id"), userStart); 
        
        hm = proc.getDBHandler().selectOneRecord(
                "SELECT * FROM "+Constants.TABLE.REQUEST_CHANGES+" WHERE id = ?",
                new String[]{proc.getRecId()}
        );
        
        if(hm != null){
            userStart = hm.getOrDefault("createdByName","").toString();
        }
                
        proc.setProcessStarter(userStart);
        
        if(!StringUtils.isBlank((String) props.get("approver_id"))){
            
            String officers = setApprovers("officer",proc);
            userIds += officers;            
            
            workflowManager.activityVariable(wfAssignment.getActivityId(), (String) props.get("approver_id"), userIds);
        }
        
        
    }
}
