/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tms.hrdc.webservice.models;

import com.tms.hrdc.dao.EmpmObj;
import com.tms.hrdc.defaultPluginTool.EmailTemplateTool;
import com.tms.hrdc.util.CommonUtils;
import com.tms.hrdc.util.Constants;
import com.tms.hrdc.util.DBHandler;
import com.tms.hrdc.util.HttpUtil;
import com.tms.hrdc.util.KeywordDictionary;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.parser.ParseException;

/**
 *
 * @author faizr
 */
public class PEData {
    
    private void msg(String msg){
        LogUtil.info(this.getClass().getName(), msg);
    }
    
    HttpServletRequest request;
    DBHandler db;
    
    public PEData(DBHandler d, HttpServletRequest req) throws SQLException {
        db = d;
        request = req;        
    }
    
    class PEAPIException extends Exception
    {
        public PEAPIException(String message)
        {
            super(message);
        }
    }
    
    public JSONObject buildContent() throws JSONException, IOException, ParseException, SQLException{
        JSONObject reqBody = HttpUtil.getRequestBody(request);   
        String type = reqBody.optString("type", "");
        JSONArray peIds = reqBody.getJSONArray("peId");
        
        if(type.isEmpty()){
            JSONObject obj = new JSONObject();
            obj.put("result", "FAILED");
            obj.put("msg", "No Mail Type Field");
            obj.put("mail_count", "0");

            return obj;
        }        
        
        if(!type.toLowerCase().equals("letter") && !type.toLowerCase().equals("email")){
            JSONObject obj = new JSONObject();
            obj.put("result", "FAILED");
            obj.put("msg", "Invalid mailType values (Should be Email or Letter)");
            obj.put("mail_count", "0");

            return obj;
        }
        
        return buildLetterContents(type.toUpperCase(), peIds);
    }
    
    private JSONObject buildLetterContents(String mailType, JSONArray peIds) throws JSONException, IOException, ParseException, SQLException{
        
        JSONObject respo = new JSONObject();
        
        String listId = "";
        boolean hasValidEmp = true;
        int noMailCount = 0;
        int builtCount = 0;
        
        //check if emp sent is valid employer
        hasValidEmp =  checkValidEmp(peIds, hasValidEmp);
        ArrayList<HashMap<String, String>> empList = getAllEmps(peIds);
        
        if(empList.size()==0){
            respo.put("result", "FAILED");
            respo.put("msg", "No data for the employers");
            respo.put("mail_count", "0");

            return respo;
        }
        
        HashMap listHm = new HashMap();
        listHm.put("mailType", mailType);
        listHm.put("status", "PREVIEW");
        listHm.put("mailCount", Integer.toString(empList.size()));

        listId = CommonUtils.saveUpdateForm2("", Constants.FORM_ID.POTEMP_MAIL_LIST, "", listHm);
        
        if(listId.isEmpty()){
            respo.put("result", "FAILED");
            respo.put("mail_count", "0");

            return respo;
        }
        
        HashMap emailTemplate = EmailTemplateTool.getEmailTemplate(db, Constants.MAIL_TYPE.POT_EMP.ORDER);
        String subjectTemplate = emailTemplate.get("c_template_subject").toString();
        String contentTemplate = emailTemplate.get("c_template_content").toString();
        
        for(HashMap empHm:empList){    
            String peId = empHm.get("PE").toString();
            String empId = empHm.get("EMP").toString();
            
            if(empId.isEmpty()){
                continue;
            }
            
            //save to form 
            EmpmObj empObj = new EmpmObj(db, EmpmObj.BY_ID, empId);
            HashMap peHm = db.selectOneRecord(
                    "SELECT c_batch FROM "+Constants.TABLE.POT_EMP
                    +" WHERE id=? ",
                    new String[]{peId}
            );
            
            String peBatchId = "";
            if(peHm!=null){
                peBatchId = peHm.get("c_batch").toString();
            }
            
            String ref = "PE/MAIL/"+CommonUtils.get_DT_CurrentDateTime("YYYY")+"/"
                +CommonUtils.getRefNo("6",Constants.ENV_VAR.POT_EMP.PE_MAIL_LIST_COUNTER);
            
            HashMap peMailHm = new HashMap();
            peMailHm.put("comp_name", empObj.getCompName());
            peMailHm.put("mail_fk", empId);
            peMailHm.put("list_fk", listId);
            peMailHm.put("batch_id", peBatchId);
            peMailHm.put("mailType", mailType);
            peMailHm.put("mailRef", ref);            
            peMailHm.put("letter", "");            
            
            String mail_to = "";
            
            if(mailType.equals("EMAIL")){
                mail_to = empObj.getPrimaryEmail();
            }else{                
                mail_to = empObj.getFullBusinessAddress();
            }
            
            if(mail_to.isEmpty()){
                noMailCount++;
                continue;
            }else{
                builtCount++;
                peMailHm.put("mail_to", mail_to);
            }
            
            String mailId = CommonUtils.saveUpdateForm2("", Constants.FORM_ID.MAIL_PREVIEW, "", peMailHm);
            
            //Building Content
            KeywordDictionary kwd = new KeywordDictionary(db);
            
            String subject = kwd.buildContent(db, subjectTemplate, empId, mailId);
            String message = kwd.addTracker(contentTemplate);
            message = kwd.buildContent(db, contentTemplate, empId, mailId);            
            message = "<span style='font-family:Segoe UI,Roboto,Helvetica Neue,sans-serif'>"+message+"</span>";            
            
            peMailHm.put("mail_subject", subject);
            peMailHm.put("mail_content", message);                   
            
            CommonUtils.saveUpdateForm2(Constants.APP_ID.EMPM, Constants.FORM_ID.MAIL_PREVIEW, mailId, peMailHm);
        }
        
        //TODO
        //generate files
        
        
        respo.put("result", "SUCCESS");
        respo.put("mail_count", Integer.toString(builtCount));
        respo.put("no_mail_count", Integer.toString(noMailCount));
        respo.put("listId", listId);
        
        return respo;
    }
    
    private boolean checkValidEmp(JSONArray peIds, boolean hasValidEmp) throws JSONException{
        for(int x=0;x<peIds.length();x++){
            JSONObject peId_jo = peIds.getJSONObject(x);
            
            if(peId_jo==null){
                hasValidEmp=false;
                break;
            }
            
            String peId = peId_jo.getString("id");
            
            ArrayList<HashMap<String, String>> batchId = CommonUtils.getPeId_batch(db, peId);
            
            if(batchId.size()>0){
                for(HashMap hm:batchId){
                    String peId_ = hm.getOrDefault("id", "").toString();
//                    checkValidEmp(peIds, hasValidEmp)
                }
            }
            
            String empId = CommonUtils.getEmpId_PotEmp(db, peId);
            
            if(empId.isEmpty()){                
                hasValidEmp=false;
                break;
            }
        }
        
        return hasValidEmp;
    }

    private ArrayList<HashMap<String, String>> getAllEmps(JSONArray peIds) throws JSONException {
        ArrayList<HashMap<String, String>>  list = new ArrayList();
        String empId = "";
        for(int x=0;x<peIds.length();x++){
            JSONObject peId_jo = peIds.getJSONObject(x);
            
            if(peId_jo==null){
                continue;
            }
            
            String peId = peId_jo.getString("id");
            
            ArrayList<HashMap<String, String>> batchId = CommonUtils.getPeId_batch(db, peId);
            
            for(HashMap hm:batchId){
                String peId_ = hm.getOrDefault("id", "").toString();
                empId = CommonUtils.getEmpId_PotEmp(db, peId_);
                
                if(!empId.isEmpty()){
                    HashMap hm_ = new HashMap();
                    hm_.put("PE", peId_);
                    hm_.put("EMP", empId);
                    list.add(hm_);
                }
            }
            
            empId = CommonUtils.getEmpId_PotEmp(db, peId);
            
            if(!empId.isEmpty()){                
                HashMap hm_ = new HashMap();
                hm_.put("PE", peId);
                hm_.put("EMP", empId);
                list.add(hm_);
            }
        }
        
        return list;
    }
    
    
    public JSONObject getTotalUploadedReport() throws PEAPIException{        
        
        String batchId = request.getParameter("batchId")==null?"":request.getParameter("batchId");
        
        if(batchId.isEmpty()){
            throw new PEAPIException("PARAMETER `batchId` must be included");  
        }
        
        String peCount = db.selectOneValueFromTable(
                "SELECT count(p.id) as count FROM "+Constants.TABLE.POT_EMP+" p WHERE c_batch = ? ", 
                new String[]{batchId}
        );
        
        if(peCount.isEmpty() || peCount.equals("0")){
            peCount = db.selectOneValueFromTable(
                "SELECT count(p.id) as count FROM "
                        +Constants.TABLE.POT_EMP_UPLOAD_DATA
                        +" p WHERE c_batch = ? ", 
                new String[]{batchId}
            );
        }  
        peCount=peCount.isEmpty()?"0":peCount;
                
        JSONObject data = new JSONObject();
        data.put("count", peCount);
        
        return data;
    }
    
    
    
    //Returns numbers from PE in a batch
    public JSONObject getCurrentPECount() throws PEAPIException{
        
        String status = request.getParameter("status")==null?"":request.getParameter("status");
        String batchId = request.getParameter("batchId")==null?"":request.getParameter("batchId");
        
        if(status.isEmpty()){
            throw new PEAPIException("PARAMETER `status` must be included");  
        }
        if(batchId.isEmpty()){
            throw new PEAPIException("PARAMETER `batchId` must be included");  
        }
        
        String query = "SELECT count(id) as count FROM app_fd_empm_pe_potEmp ";
        String url = CommonUtils.getBaseURL()+"/jw/web/userview/empm/emp/_/";
        
        switch(status){
            case "ALL_UPLOADED":
                
                String isPotEmp = db.selectOneValueFromTable(
                        "SELECT id FROM "+Constants.TABLE.POT_EMP+" WHERE c_batch = ? LIMIT 1", 
                        new String[]{batchId});
                
                if(isPotEmp.isEmpty()){
                    query = "SELECT count(d.id) as count FROM "
                        +Constants.TABLE.POT_EMP_UPLOAD_DATA+" d "
                        + "INNER JOIN "+Constants.TABLE.POT_EMP_UPLOAD+" u ON u.id = d.c_batch "
                        +" WHERE u.id = ? AND d.c_status = 'SUCCESS'";
                }else{
                    query ="select count(d.id) as count "
                        + "FROM app_fd_empm_pe_upl_data d \n" +
                        "WHERE c_batch IN \n" +
                        "(\n" +
                        "SELECT c_batch_before_merge FROM app_fd_empm_pe_potEmp p \n" +
                        "WHERE p.c_batch = ? \n" +
                        ")";
                }
//                url=CommonUtils.getBaseURL()+"/jw/web/userview/empm/emp/_/successfully_uploaded_crud";
                url+="pe_pending_all_uploaded";
            break;    
            case "SUCCESS":
                query+=
                        "WHERE c_batch = ? "
                        + " AND c_isPotEmp = 'Y' "
                        + "AND (c_potEmpDuplId is null OR c_potEmpDuplId = '') ";
                url+="pe_success_link";
            break;    
            case "UNSUCCESS":
                query+=
                        "WHERE c_batch = ? "
                        + " AND c_status = 'POTENTIAL_REJECTED' ";
                url+="pe_error_link";
            break;    
            case "PENDING_ACTION":
                query+=
//                        " -- AND c_status = 'POTENTIAL' "
                        " WHERE c_isPotEmp = 'Y' "
                        + "AND c_potEmpDuplId = ? ";
                url+="pe_pending_action_link";
            break;    
        }
        
//        msg("query = "+query);
        
        url+="?batch="+batchId;
        
        HashMap hm = db.selectOneRecord(query, new String[]{batchId});      
        String count = hm!=null?hm.getOrDefault("count", "0").toString():"0";
        
        String html = "<a href='"+url+"'> "+count+"</a>";
        
        JSONObject data = new JSONObject();
        data.put("url", url);
        data.put("html", html);
        data.put("count", count);
        
        return data;
    }
    
    //Returns number of TPE in a batch
    public JSONObject getCurrentTPECount(){
        
        
        return new JSONObject();
    }
    
}
