/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tms.hrdc.hashVariable;

import bsh.Interpreter;
import com.tms.hrdc.dao.CurrentUser;
import com.tms.hrdc.util.DBHandler;
import java.util.HashMap;
import java.util.Map;
import org.joget.apps.app.dao.EnvironmentVariableDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DefaultHashVariablePlugin;
import org.joget.apps.app.model.EnvironmentVariable;
import static org.joget.apps.app.service.AppPluginUtil.executeScript;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.springframework.context.ApplicationContext;

/**
 *
 * @author faizr
 */
public class EmpHashVariable extends DefaultHashVariablePlugin{

    @Override
    public String getName() {
        return this.getClass().getName();
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "HRDC -EMPM - Custom hash variable";
    }

    @Override
    public String getLabel() {
        return "HRDC -EMPM - Custom hash variable";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return "";
    }
    
    private void msg(String msg){
        LogUtil.info(this.getClassName(), msg);
    }
    
    @Override
    public String getPrefix() {
        return "currentEmp";
    }

    @Override
    public String processHashVariable(String variableKey) {
            CurrentUser cu = new CurrentUser();
            
            String usrId = cu.getId();
            String mycoid = "";
            String psmbno = "";
            String old_mycoid = "";
            String empId = "";
            
            DBHandler db = new DBHandler();
            
            try{
                db.openConnection();
                
                HashMap empHm = db.selectOneRecord(
                        "SELECT r.id, r.c_mycoid, r.c_mycoid_old, r.c_hrdc_no "
                        + "FROM app_fd_empm_usermap u "
                        + "INNER JOIN app_fd_empm_reg r ON r.id = u.c_compId "
                        + "WHERE u.c_userId = ? ",
                        new String[]{usrId}
                );
                
                if(empHm!=null){
                    mycoid = empHm.getOrDefault("c_mycoid", "").toString();
                    old_mycoid = empHm.getOrDefault("c_mycoid_old", "").toString();
                    psmbno = empHm.getOrDefault("c_hrdc_no", "").toString();
                    empId  = empHm.getOrDefault("id", "").toString();
                }
                
            }catch(Exception e){
                e.printStackTrace();
            }finally{
                db.closeConnection();
            }
            
            String return_val = "";
            
            switch(variableKey){
                case "psmbno":
                    return_val = psmbno;
                break;
                case "mycoid":
                    return_val = mycoid;
                break;
                case "oldMycoid":
                    return_val = old_mycoid;
                break;
                case "id":
                    return_val = empId ;
                break;
            }
            
            return return_val;
    }
    
    protected String executeScript(String script, Map properties) throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.setClassLoader(getClass().getClassLoader());
        for (Object key : properties.keySet()) {
            interpreter.set(key.toString(), properties.get(key));
        }
        LogUtil.debug(getClass().getName(), "Executing script " + script);
        return (String) interpreter.eval(script);
    }
    
}
