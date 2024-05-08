/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tms.hrdc.binder;

import com.tms.hrdc.defaultPluginTool.EmailTemplateTool;
import com.tms.hrdc.util.CommonUtils;
import com.tms.hrdc.util.Constants;
import com.tms.hrdc.util.DBHandler;
import com.tms.hrdc.util.KeywordDictionary;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.lib.WorkflowFormBinder;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;
import org.joget.commons.util.UuidGenerator;
import org.joget.directory.model.User;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcessLink;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;

/**
 *
 * @author faizr
 */
public class QueryBinder extends WorkflowFormBinder{
    
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
        return "Used in Query Processing, contains load binder and store binder";
    }

    @Override
    public String getLabel() {
        return "HRDC - EMPM - Query Binder";
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
        LogUtil.info(this.getClass().toString(), msg);
    }
    
    @Override
    public FormRowSet store(Element element, FormRowSet rows, FormData formData) {
        FormRow row = rows.get(0);
        String mail_content = row.getProperty("mail_content");
//        String id = row.getId();
//        String id = UuidGenerator.getInstance().getUuid();
//        rows.get(0).setId(id);
        
        msg("storing "+rows.get(0).getId());
        
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");  
        LocalDateTime now = LocalDateTime.now();  
        row.setProperty("query_date",dtf.format(now));
        
        final FormRowSet rows_ = super.store(element, rows, formData);        
        return rows_;  
    }

    private void updateQueryFields(FormRowSet rows, DBHandler db) {
        
        FormRow row = rows.get(0);
        String pkv = row.getProperty("fk");
        String id = row.getId();
 
        String query = "SELECT * FROM app_fd_empm_reg WHERE id = ?";
        String[] cond = {pkv};
        
        HashMap hm = db.selectOneRecord(query, cond);        

        if(hm == null || hm.size()<0){
            return;
        }
        
        String hrdc_no = hm.get("c_hrdc_no") == null?"":hm.get("c_hrdc_no").toString();
        
        String suffix = UuidGenerator.getInstance().getUuid().substring(0, 8);
        if(!id.contains("query")){
            rows.get(0).setId("query-"+hrdc_no+"-"+suffix);
        }        
        rows.get(0).setProperty("req_mycoid", hrdc_no);
    }

    public String getSpecialCase(String case_id, String original, Map jsonMap) {
        String value;
        
        if (case_id.equals("appl_address")) {
            String address1 = (String) jsonMap.getOrDefault("c_bu_address1","");
            if (address1.isEmpty())
                return original;
            String address2 = (String) jsonMap.getOrDefault("c_bu_address2","");
            if (address2.isEmpty())
                return original;
            String address3 = (String) jsonMap.getOrDefault("c_bu_address3","");
            if (address3.isEmpty())
                return original;
            String postcode = (String) jsonMap.getOrDefault("c_bu_postcode","");
            if (postcode.isEmpty())
                return original;
            String city = (String) jsonMap.getOrDefault("c_bu_city","");
            if (city.isEmpty())
                return original;
            String state = (String) jsonMap.getOrDefault("c_bu_state","");
            if (state.isEmpty())
                return original;
            
            value = address1 + "<br>" + address2 + "<br>" + (address3.isEmpty()?"":address3+"<br>") + postcode + " " + city + "<br>" + state;
        } else {
            value = original;
        }
        
        return value;
}
    
    String recordId = "";
        
    @Override
    public FormRowSet load(Element element, String id, FormData formData) {
        
        msg("Template loda binder starting");
            
        if(!StringUtils.isBlank(formData.getProcessId())){
            WorkflowManager wm = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
            WorkflowProcessLink wpl = wm.getWorkflowProcessLink(formData.getProcessId());
            recordId = wpl.getOriginProcessId();
        }
                
        FormRowSet rows = new FormRowSet();
//        FormRow row = new FormRow();
//        row.setId(id);
//        rows.add(row);
        
        if(StringUtils.isBlank(id)){
            
            return rows;
        }
        
        DBHandler db = new DBHandler();
        
        try {
            
            db.openConnection((DataSource) AppUtil.getApplicationContext().getBean("setupDataSource"));
            msg("hello--------------------------");
            int openingCurl = id.indexOf('{');
            Map jsonMap = new HashMap();
            msg("2--------------------------");
            if (openingCurl >= 0) {
                String jsonInput = id.substring(openingCurl, id.indexOf('}', openingCurl) + 1);
                msg("2--------------------------");
                id  = id.replace("_" + jsonInput,  "");
                msg("3--------------------------");
                
                int quoteindex = jsonInput.indexOf('"');
                int endingquoteindex;
                int nQuote;
                String str;
                String tempstr;
                int i = 0;
                while (quoteindex > 0)
                {
                    msg("1--------------------------");
                    endingquoteindex = quoteindex;
                    if (i > 100) {
                        break;
                    }
                    msg("2--------------------------");
                    nQuote = 4;
                    while (nQuote > 1) {
                        endingquoteindex = jsonInput.indexOf("\"", endingquoteindex + 1);
                        nQuote--;
                    }
                    msg("3--------------------------");
                    //getting the substring or the json data and removing the quotes
                    str = jsonInput.substring(quoteindex, endingquoteindex + 1);
                    LogUtil.info("content","str: " + str);
                    msg("4--------------------------");
                    
                    tempstr = str.replaceAll("\"", "");
                    String[] keyval = tempstr.split(":");
                    if (keyval.length == 2)
                        jsonMap.put(keyval[0], keyval[1]);
                    else
                        jsonMap.put(keyval[0], "");
                    msg("5--------------------------");
                    //remove substrings
                    jsonInput = jsonInput.replace(str, "");
                    
                    LogUtil.info("content","i: " + i);
                    quoteindex = jsonInput.indexOf("\"");
                    msg("6--------------------------");
                    i++;
                }
            }

            String[] rawValue = id.split("_"); //0-tag, 1-emp_reg id, 2-tmeplate id, 3-reason
            
            msg("raw _size "+Integer.toString(rawValue.length));
            msg("passed value "+id);
            if(rawValue.length<2){
                db.closeConnection();
                return rows;
            }

            //- --------------------------------------------------------------------
            // add email
            String rejectReason = "";
            String queryReason = "";
            String subReason = "";
            String isf5 = "";
            
            
            try{
                queryReason = rawValue[2]==null?"":rawValue[2];
                rejectReason = rawValue[2] == null ? "" : rawValue[2];
                subReason = rawValue[2] == null?"":rawValue[2];
            }catch(Exception e){
//                LogUtil.info("Query binder", "No Reason selected");
            }
            
            String mailTemplateId = rawValue[1]==null?"":rawValue[1];
            String empId = rawValue[0]==null?"":rawValue[0];
//            id = rawValue[0]==null?"":rawValue[0];
            if(mailTemplateId==null || empId.contains("#")){
                db.closeConnection();
                return rows;
            }

//            id = id+"_"+empId+"_"+mailTemplateId;
            
//            HashMap hm = EmailTemplateTool.getEmailTemplate(db, mailType);
            HashMap hm = db.selectOneRecord(
                     "SELECT \n" +
                    "	mt.id mailType, CONCAT(mt.c_moduleType, ' - ',mt.c_emailType) templ_name,\n" +
                    "	s.c_template_subject, s.c_template_content\n" +
                    "FROM app_fd_empm_email_stp mt\n" +
                    "INNER JOIN app_fd_empm_template_stp s ON mt.id = s.c_email_fk " + 
                    "AND s.id = ?",
                    new String[]{mailTemplateId}
            );
                    
//            hm.put("mail_type", mailTemplateId);
            
            hm.put("mail_fk", empId);
            hm.put("id", id);
            hm.put("query_reason", queryReason);
            hm.put("reject_reason",rejectReason);
            hm.put("sub_reason",subReason);
            
            //handle for f5
            try{
                isf5 = rawValue[3]==null?"":rawValue[3];
                
                if(!isf5.isEmpty()){
                    String f5EmpId = CommonUtils.getEmpId_empDeregF5(db, id);
                    hm.put("mail_fk_f5", f5EmpId);
                }
            }catch(Exception e){
//                LogUtil.info("Query binder", "No Reason selected");
            }
            
            
            msg("Loading template "+mailTemplateId);

            
            if(hm != null){                
                String template = "#form.empm_template_stp.template_content[" + mailTemplateId + "]#";
                String templatehash = AppUtil.processHashVariable(template,formData.getAssignment(),null,null);
                
                int hashindex = templatehash.indexOf("[");
                String hash;
                String newhash;
                String tempstr;
                int closingIndex = 0;
                int i = 0;
                
                //This while loop goes through the template and replaces all hash variables with its values
                while (hashindex > 0)
                {
                    //Gettng the hash and removing unwanted characters
                    closingIndex = templatehash.indexOf("]", hashindex + 1);
                    hash = templatehash.substring(hashindex, closingIndex + 1);
                    newhash = hash.substring(1, hash.length() - 1);
                    
                    //Fetching the data from hash variables
                    tempstr = (String) jsonMap.get(newhash);
                    if (tempstr != null)
                        newhash = tempstr;
                    msg("newhash "+ newhash);
                    //Replace hash variable with data
                    if (hash.equals("[" + newhash + "]") || newhash.equals("")) {
                        newhash = getSpecialCase(newhash, hash, jsonMap);
                    }
                    if (newhash.equals(hash)) {
                        hashindex = templatehash.indexOf("[", closingIndex);
                    } else {
                        templatehash = templatehash.replace(hash, newhash);
                        hashindex = templatehash.indexOf("[", hashindex);
                    }
                    if (i > 50) {
                        break;
                    }
                    i++;
                }

                msg("templatehash "+ templatehash);

                rows = getRows(db, hm, templatehash);    
                String mail_content = rows.get(0).getProperty("mail_content");
            }
            
        } catch (SQLException ex) {
            ex.printStackTrace();
        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
        }finally {
            db.closeConnection();
        }
        
        return rows;
    }
    
    public boolean isQueryTrue(String query, String id, DBHandler db){
        try{
            db.openConnection((DataSource) AppUtil.getApplicationContext().getBean("setupDataSource"));
        }catch(Exception e){            
        }          
        
        String[] cond = {id};            

        HashMap<String, String> hm = db.selectOneRecord(query, cond);

        db.closeConnection();
        
        if(hm != null && !hm.isEmpty()){
            return true;
        }else{
            return false;
        }
    }

    private FormRowSet getRows(DBHandler db, HashMap hm, String templatehash) throws SQLException, UnsupportedEncodingException{
        
        String subject = hm.get("c_template_subject")==null?"":hm.get("c_template_subject").toString();
        String content = templatehash;
        String remark = hm.get("remark")==null?"":hm.get("remark").toString();
        String empId = hm.get("mail_fk")==null?"":hm.get("mail_fk").toString();     // empId
        String type = hm.get("mailType")==null?"":hm.get("mailType").toString();  // formerly ET--
        String id = hm.get("id")==null?"":hm.get("id").toString();                  //random
        String query_reason = hm.get("query_reason")==null?"":hm.get("query_reason").toString();
        String reject_reason = hm.get("reject_reason")==null?"":hm.get("reject_reason").toString();
        String sub_reason = hm.get("sub_reason")==null?"":hm.get("sub_reason").toString();
        
        String empId_f5 = "";
        
        try{
            empId_f5 = hm.get("mail_fk_f5")==null?"":hm.get("mail_fk_f5").toString();     // empId for f5
        }catch(Exception e){
            
        }
        
        KeywordDictionary kwd = new KeywordDictionary(db);
        kwd.setIsPreview();
        
        if(!recordId.isEmpty()){
            kwd.setRecordId(recordId);            
        }        
        if(!query_reason.isEmpty()){
            kwd.buildQueryReason(db,query_reason);
            query_reason = kwd.getBuiltQueryReason();
        }
        
        if(!reject_reason.isEmpty()){
            kwd.buildRejectReason(db,reject_reason);
            reject_reason = kwd.getBuiltRejectReason();
        }
        
        if(!sub_reason.isEmpty()){
            kwd.buildSubReason(db,sub_reason);
            sub_reason = kwd.getBuiltSubReason();
        }
        
        
        String parsedSubject = kwd.buildContent(db, subject, empId, id);
        String parsedMsg = kwd.buildContent(db, content, empId, id); 
                
        FormRowSet rows = new FormRowSet();
        FormRow row = new FormRow();
        
        HashMap cu = CommonUtils.getCurrentUser();
        
        msg("fk id "+id);
        
        if(hm!= null && !hm.isEmpty()){
            row.setCreatedByName(cu.get("firstName").toString()+" "+cu.get("lastName").toString());
            row.setCreatedBy(cu.get("username").toString());
            row.setProperty("mail_subject", parsedSubject);
            row.setProperty("mail_remark", remark);
            row.setProperty("mail_content", parsedMsg);
            row.setProperty("mail_fk", empId_f5.isEmpty()?empId:empId_f5);
            row.setProperty("mail_type", type);
            row.setProperty("is_seen", Constants.STATUS.EMAIL.SENT);
//            row.setProperty("query_reason", query_reason);
//            row.setProperty("reject_reason",reject_reason);
//            row.setProperty("sub_reason",sub_reason);
            row.setId(id);
//            LogUtil.info("hm: ", "not empty");
        }
        else{
           msg("hm: empty!");
        }
        
        rows.add(row);

        return rows;        
    }

    private String getEmpEmail(DBHandler db, String empId) {
        String sql =  "SELECT d.email FROM app_fd_empm_usermap u "
        + " INNER JOIN dir_user d ON d.id = u.c_userId "
        + "WHERE u.c_compId = ?";
        
        HashMap hm = db.selectOneRecord(sql, new String[]{empId});
        
        if(hm!=null){
            return hm.get("email").toString();
        }
        
        return "";
    }
    
}
