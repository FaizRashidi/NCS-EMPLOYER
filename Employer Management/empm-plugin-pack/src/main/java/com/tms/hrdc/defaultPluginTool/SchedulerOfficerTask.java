/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tms.hrdc.defaultPluginTool;

import static com.tms.hrdc.defaultPluginTool.EmailTemplateTool.getEmailTemplateNEW;
import static com.tms.hrdc.defaultPluginTool.SchedulerQueryReminder.url_contextPath;
import static com.tms.hrdc.defaultPluginTool.SchedulerQueryReminder.url_scheme;
import static com.tms.hrdc.defaultPluginTool.SchedulerQueryReminder.url_serverName;
import static com.tms.hrdc.defaultPluginTool.SchedulerQueryReminder.url_serverPort;
import com.tms.hrdc.util.CommonUtils;
import com.tms.hrdc.util.Constants;
import com.tms.hrdc.util.DBHandler;
import com.tms.hrdc.util.HttpUtil;
import com.tms.hrdc.util.KeywordDictionary;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.apache.http.HttpHeaders;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.json.JSONException;
import org.json.JSONObject;
    
/**
 *
 * @author faizr
 */
public class SchedulerOfficerTask extends DefaultApplicationPlugin{

    @Override
    public String getName() {
        return this.getClass().toString();
    }

    @Override
    public String getVersion() {
        return "69";
    }

    @Override
    public String getDescription() {
        return "Used with a scheduler to send reminders to any unresponded approval tasks";
    }

    @Override
    public String getLabel() {
        return "HRDC - EMPM - Modified Scheduler Notification Tool";
    }

    @Override
    public String getClassName() {
        return this.getClass().toString();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/data_collector_json.json", null, true, "message/emailConfig");
    }
    
    PluginManager pluginManager = null;
    WorkflowManager workflowManager = null;
    WorkflowAssignment wfAssignment = null;
    
    static String url_scheme = "";
    static String url_serverName = "";
    static String url_serverPort = "";
    static String url_contextPath = "";
    
    @Override
    public Object execute(Map props) {
        
        DBHandler db = new DBHandler();
        
        wfAssignment = (WorkflowAssignment) props.get("workflowAssignment");             
        pluginManager = (PluginManager) props.get("pluginManager");
        workflowManager = (WorkflowManager) pluginManager.getBean("workflowManager"); 
        
        url_scheme = getPropertyString("url_scheme");
        url_serverName = getPropertyString("url_serverName");
        url_serverPort = getPropertyString("url_serverPort");
        url_contextPath = getPropertyString("url_contextPath");
               
        String mailId = "";
        String subject = "";
        String message = "";
        String regId = "";
        String originProcessId = "";
        String receiver = "";
        String toEmail = "";
        String activityId = "";
        
        try {
            db.openConnection((DataSource) AppUtil.getApplicationContext().getBean("setupDataSource"));

            String query = "SELECT shk.*, w.originProcessId, r.id AS regId\n" +
                "FROM SHKAssignmentsTable shk\n" +
                "INNER JOIN wf_process_link w ON w.processId = shk.ActivityProcessId \n" +
                "INNER JOIN app_fd_empm_regAppl a ON a.id = w.originProcessId \n" +
                "INNER JOIN app_fd_empm_reg r ON r.id = a.c_empl_fk\n" +
                "WHERE ActivityId LIKE '%empm%' AND (ActivityId LIKE '%verification%'\n" +
                "				OR ActivityId LIKE '%approval%'\n" +
                "                               OR ActivityId LIKE '%query%')";
            
            ArrayList<HashMap<String, String>> data = db.select(query);
            
            KeywordDictionary kwd = new KeywordDictionary(db);
            

            for(HashMap hm : data) {
                originProcessId = hm.get("originProcessId").toString();
                LogUtil.info("OriginProcessId", originProcessId);
                regId = hm.get("regId").toString();
                receiver = hm.get("ResourceId").toString();
                kwd.setRecordId(regId);
                activityId = hm.get("ActivityId").toString();
                HashMap templHm;
                if (activityId.contains("empm_emp_registration_approval1")){
                    templHm = getEmailTemplateNEW(db, "T0030");
                }
                else if (activityId.contains("empm_emp_deregistration_dereg_verify_form4")){
                    templHm = getEmailTemplateNEW(db, "T0046");
                }
                else if (activityId.contains("empm_emp_deregistration_dereg_approval_form4 ")){
                    templHm = getEmailTemplateNEW(db, "T0045");
                }
                else{
                    return null;
                }
                subject = kwd.buildContent(db, templHm.get("c_template_subject").toString(), regId, "");
                message = kwd.buildContent(db, templHm.get("c_template_content").toString(), regId, "");
                message = "<span style='font-family:Segoe UI,Roboto,Helvetica Neue,sans-serif'>"+message+"</span>";
                toEmail = getEmail(db, receiver);
 
                if(toEmail != null && !toEmail.equals("")) {
                    CommonUtils.sendEmail(toEmail, "", subject, message, "", null);
                } else {
                    LogUtil.info("Email empty", "dir_user email empty");
                } 
            }              
        } catch(Exception e) {
            Logger.getLogger(this.getClassName()).log(Level.SEVERE, null, e);
        } finally {
            db.closeConnection();
        }
        return null;
    }
    
    private String getEmail(DBHandler db, String id) {
        String email = "";
        String query = "SELECT * "
                + "FROM dir_user "
                + "WHERE id = ? ";
        
        HashMap hm = db.selectOneRecord(query, new String[]{id});
        if(hm != null) {
            email = (String) hm.get("email").toString() != null ? (String) hm.get("email").toString() : "";
        }
        
        
        return email;

    }
}
