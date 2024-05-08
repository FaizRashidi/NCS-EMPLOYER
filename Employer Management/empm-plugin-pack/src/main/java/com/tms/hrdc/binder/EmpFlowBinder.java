/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tms.hrdc.binder;

import com.tms.hrdc.dao.EmpmObj;
import com.tms.hrdc.util.CommonUtils;
import com.tms.hrdc.util.Constants;
import com.tms.hrdc.util.DBHandler;
import java.util.HashMap;
import java.util.Map;
import org.joget.apps.form.lib.WorkflowFormBinder;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowAssignment;

/**
 *
 * @author faizr
 */
public class EmpFlowBinder extends WorkflowFormBinder{
    
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
        return "Used in main flow's loadbinder";
    }

    @Override
    public String getLabel() {
        return "HRDC - EMPM - Main Flow Binder";
    }

    @Override
    public String getClassName() {
        return this.getClass().toString();
    }

    @Override
    public String getPropertyOptions() {
       return "";
    }
    
    private void pm(String msg){
        LogUtil.info(this.getClassName(), msg);
    }
    
    public FormRowSet load(Element element, String id, FormData formData) {
        
        FormRowSet rows = new FormRowSet();

        String pKey = formData.getPrimaryKeyValue();
        
        String formId = super.getFormId();
        String table = super.getTableName().startsWith("app_fd_")?super.getTableName():"app_fd_"+super.getTableName();
        DBHandler db = new DBHandler();
        
        switch(formId){
            case "": // -- > form ID of reg
                //DO UPDATE BRANCH DATA
                // use msg() function to log (lie 55)
                updateBranchMycoid(db, pKey);
            break;
            
            default:
                updateViewStatus(db, formId, table, pKey);
        }
                
        return rows;
    }
    
    @Override
    public FormRowSet store(Element element, FormRowSet rowSet, FormData formData) {
        
        rowSet = super.store(element, rowSet, formData);
        
        return rowSet;
    }
    
    private void updateViewStatus(DBHandler db, String formId, String table, String pKey){
        
        try{
            db.openConnection();
            
            int upd = db.update("UPDATE "+table+" SET c_is_viewed = ? WHERE id = ?",
                    new String[]{Constants.STATUS.VIEW_STATUS.PENDING},
                    new String[]{pKey});
            
        }catch(Exception e){
            e.printStackTrace();
            HashMap hm = new HashMap();
            hm.put("is_viewed", "PENDING");
            CommonUtils.saveUpdateForm("", formId, pKey, hm);            
        }finally{
            db.closeConnection();
        }
        
    }
    
    private String getHQIDNo(DBHandler db, String mycoid) {
        String query = "SELECT id FROM app_fd_empm_reg WHERE c_mycoid = ? AND c_empl_reg_type = 'HQ'";
        
        HashMap hm = db.selectOneRecord(query, new String[]{mycoid});
        
        if(hm!=null){
            return hm.get("id").toString();
        }
        return "";
    }
      
    private void updateBranchMycoid(DBHandler db, String pKey){
        try{
            db.openConnection();
            EmpmObj obj = new EmpmObj(db, EmpmObj.BY_ID, pKey);
            String mycoid = obj.getMycoid();
            String reg_type = obj.getRegType();
            //Refer function in RefNumGenerator Class Line 136
            String branch_mycoid = "", hq_emp_id = "";
            if(reg_type.equals("Branch")){
                hq_emp_id = getHQIDNo(db, mycoid);
                branch_mycoid = obj.generateBranchMyCoID(mycoid); //hrdcB0001
            }
            
            String query = "UPDATE app_fd_empm_reg SET c_hq_id = ?, c_hq_mycoid = ?, c_branch_mycoid = ?, c_mycoid = ? WHERE id = ?";
            int i = db.update(query, new String[]{hq_emp_id,mycoid,branch_mycoid,branch_mycoid}, new String[]{pKey});
            
            LogUtil.info(this.getClassName(),"BRANCH DATA UPDATE "+Integer.toString(i));
            
        }catch(Exception e){
            e.printStackTrace();   
        }finally{
            db.closeConnection();
        }
    }
}
