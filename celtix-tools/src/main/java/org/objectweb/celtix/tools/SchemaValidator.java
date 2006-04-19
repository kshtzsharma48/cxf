package org.objectweb.celtix.tools;

import java.util.HashSet;
import java.util.Set;

import org.objectweb.celtix.common.i18n.Message;
import org.objectweb.celtix.tools.common.ProcessorEnvironment;
import org.objectweb.celtix.tools.common.ToolConstants;
import org.objectweb.celtix.tools.common.ToolException;
import org.objectweb.celtix.tools.common.toolspec.ToolRunner;
import org.objectweb.celtix.tools.common.toolspec.ToolSpec;
import org.objectweb.celtix.tools.common.toolspec.parser.BadUsageException;
import org.objectweb.celtix.tools.common.toolspec.parser.CommandDocument;
import org.objectweb.celtix.tools.common.toolspec.parser.ErrorVisitor;
import org.objectweb.celtix.tools.processors.wsdl2.validators.SchemaWSDLValidator;

public class SchemaValidator extends AbstractCeltixToolContainer {

    private static final String TOOL_NAME = "schemavalidate";
    private static String[] args;

    public SchemaValidator(ToolSpec toolspec) throws Exception {
        super(TOOL_NAME, toolspec);
    }

    private Set getArrayKeys() {
        return new HashSet<String>();     
    }
    
    public void execute(boolean exitOnFinish) {
        try {
            super.execute(exitOnFinish);
            if (!hasInfoOption()) {
                ProcessorEnvironment env = new ProcessorEnvironment();
                env.setParameters(getParametersMap(getArrayKeys()));
                if (isVerboseOn()) {
                    env.put(ToolConstants.CFG_VERBOSE, Boolean.TRUE);
                }
                
                
                env.put(ToolConstants.CFG_CMD_ARG, args);
                
                String schemaDir = (String)env.get(ToolConstants.CFG_SCHEMA_DIR);
                if (schemaDir == null) {
                    throw new ToolException("Schema search directory should " 
                                            + "be defined before validating wsdl.");
                }

                String[] schemas = (String[])env.get(ToolConstants.CFG_SCHEMA_URL);

                boolean isDeep = false;
                
                if (env.get(ToolConstants.CFG_DEEP) != null) {
                    isDeep = true;
                }
                
                String wsdlURL = (String)env.get(ToolConstants.CFG_WSDLURL);
                
                SchemaWSDLValidator schemaValidator = new SchemaWSDLValidator(schemaDir);
                schemaValidator.validate(wsdlURL, schemas, isDeep);
            }
        } catch (ToolException ex) {
            System.err.println("Error : " + ex.getMessage());
            if (ex.getCause() instanceof BadUsageException) {
                getInstance().printUsageException(TOOL_NAME, (BadUsageException)ex.getCause());
            }
            System.err.println();
            if (isVerboseOn()) {
                ex.printStackTrace();
            }
        } catch (Exception ex) {
            System.err.println("Error : " + ex.getMessage());
            System.err.println();
            if (isVerboseOn()) {
                ex.printStackTrace();
            }
        }
    }

    public static void main(String[] pargs) {
        args = pargs;

        try {
            ToolRunner.runTool(SchemaValidator.class, SchemaValidator.class
                .getResourceAsStream(ToolConstants.TOOLSPECS_BASE + "schemavalidator.xml"), false, args);
        } catch (BadUsageException ex) {
            getInstance().printUsageException(TOOL_NAME, ex);
        } catch (Exception ex) {
            System.err.println("Error : " + ex.getMessage());
            System.err.println();
            ex.printStackTrace();
        }
    }

    public void checkParams(ErrorVisitor errors) throws ToolException {
        CommandDocument doc = super.getCommandDocument();

        if (!doc.hasParameter("wsdlurl")) {
            errors.add(new ErrorVisitor.UserError("WSDL/SCHEMA URL has to be specified"));
        }
        if (errors.getErrors().size() > 0) {
            Message msg = new Message("PARAMETER_MISSING", LOG);
            throw new ToolException(msg, new BadUsageException(getUsage(), errors));
        }
    }
}
