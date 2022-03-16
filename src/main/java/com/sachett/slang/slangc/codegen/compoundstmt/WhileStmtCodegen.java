package com.sachett.slang.slangc.codegen.compoundstmt;

import com.sachett.slang.parser.SlangParser;
import com.sachett.slang.slangc.codegen.CodeGenerator;
import com.sachett.slang.slangc.codegen.expressions.BooleanExprCodegen;
import com.sachett.slang.slangc.codegen.function.FunctionCodegen;
import com.sachett.slang.slangc.codegen.utils.delegation.CodegenDelegatable;
import com.sachett.slang.slangc.codegen.utils.delegation.CodegenDelegatedMethod;
import com.sachett.slang.slangc.symbol.symboltable.SymbolTable;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.List;

public class WhileStmtCodegen extends CodegenDelegatable {
    private FunctionCodegen functionCodegen;

    private boolean generatingWhileBlock = false;
    private Label whileLoopStartLabel = null;
    private Label whileLoopExitLabel = null;

    private CodegenDelegatable delegatedParentCodegen;
    // Delegate methods:
    @Override
    public Void visitBooleanExprAssign(SlangParser.BooleanExprAssignContext ctx) {
        return delegatedParentCodegen.visitBooleanExprAssign(ctx);
    }

    @Override
    public Void visitDeclStmt(SlangParser.DeclStmtContext ctx) {
        return delegatedParentCodegen.visitDeclStmt(ctx);
    }

    @Override
    public Void visitBooleanDeclAssignStmt(SlangParser.BooleanDeclAssignStmtContext ctx) {
        return delegatedParentCodegen.visitBooleanDeclAssignStmt(ctx);
    }

    @Override
    public Void visitNormalDeclAssignStmt(SlangParser.NormalDeclAssignStmtContext ctx) {
        return delegatedParentCodegen.visitNormalDeclAssignStmt(ctx);
    }

    @Override
    public Void visitTypeInferredDeclAssignStmt(SlangParser.TypeInferredDeclAssignStmtContext ctx) {
        return delegatedParentCodegen.visitTypeInferredDeclAssignStmt(ctx);
    }

    @Override
    public Void visitTypeInferredBooleanDeclAssignStmt(SlangParser.TypeInferredBooleanDeclAssignStmtContext ctx) {
        return delegatedParentCodegen.visitTypeInferredBooleanDeclAssignStmt(ctx);
    }

    @Override
    public Void visitBlock(SlangParser.BlockContext ctx) {
        return delegatedParentCodegen.visitBlock(ctx);
    }

    @Override
    public Void visitIfStmt(SlangParser.IfStmtContext ctx) {
        return delegatedParentCodegen.visitIfStmt(ctx);
    }

    @Override
    public Void visitWhileStmt(SlangParser.WhileStmtContext ctx) {
        return delegatedParentCodegen.visitWhileStmt(ctx);
    }

    private String className;
    private String packageName;
    private SymbolTable symbolTable;

    public void setDelegatedParentCodegen(CodegenDelegatable delegatedParentCodegen) {
        this.delegatedParentCodegen = delegatedParentCodegen;
    }

    public void setFunctionCodegen(FunctionCodegen functionCodegen) {
        this.functionCodegen = functionCodegen;
    }

    public WhileStmtCodegen(
            CodegenDelegatable delegatedParentCodegen,
            FunctionCodegen functionCodegen,
            SymbolTable symbolTable,
            String className,
            String packageName
    ) {
        super(delegatedParentCodegen.getSharedDelegationManager());

        /**
         * Register the stuff that this generator generates with the shared delegation manager.
         */
        HashSet<CodegenDelegatedMethod> delegatedMethodHashSet = new HashSet<>(List.of(CodegenDelegatedMethod.BLOCK,
                CodegenDelegatedMethod.BREAK,
                CodegenDelegatedMethod.CONTINUE
        ));
        this.registerDelegatedMethods(delegatedMethodHashSet);

        this.functionCodegen = functionCodegen;
        this.delegatedParentCodegen = delegatedParentCodegen;
        this.className = className;
        this.packageName = packageName;
        this.symbolTable = symbolTable;
    }

    @Override
    public Void visitBreakControlStmt(SlangParser.BreakControlStmtContext ctx) {
        if (generatingWhileBlock && whileLoopExitLabel != null && whileLoopStartLabel != null) {
            functionCodegen.getMv().visitJumpInsn(Opcodes.GOTO, whileLoopExitLabel);
        }
        return null;
    }

    @Override
    public Void visitContinueControlStmt(SlangParser.ContinueControlStmtContext ctx) {
        if (generatingWhileBlock && whileLoopExitLabel != null && whileLoopStartLabel != null) {
            functionCodegen.getMv().visitJumpInsn(Opcodes.GOTO, whileLoopStartLabel);
        }
        return null;
    }

    public void generateWhileStmt(SlangParser.WhileStmtContext ctx) {
        Label loopLabel = new Label();
        Label exitLoopLabel = new Label();
        this.whileLoopStartLabel = loopLabel;
        this.whileLoopExitLabel = exitLoopLabel;

        var currentStackFrame = functionCodegen.getCurrentFrameStackInfo();

        functionCodegen.getMv().visitLabel(loopLabel);
        functionCodegen.getMv().visitFrame(Opcodes.F_NEW,
                currentStackFrame.numLocals, currentStackFrame.locals,
                currentStackFrame.numStack, currentStackFrame.stack
        );

        // check condition
        BooleanExprCodegen booleanExprCodegen = new BooleanExprCodegen(
                ctx.booleanExpr(),
                symbolTable,
                functionCodegen,
                className,
                packageName
        );
        booleanExprCodegen.doCodegen();

        // if condition is false, exit loop
        currentStackFrame = functionCodegen.getCurrentFrameStackInfo();
        functionCodegen.getMv().visitJumpInsn(Opcodes.IFEQ, exitLoopLabel);

        this.generatingWhileBlock = true;

        visit(ctx.block());

        this.generatingWhileBlock = false;
        this.whileLoopStartLabel = null;
        this.whileLoopExitLabel = null;

        // start next iteration
        functionCodegen.getMv().visitJumpInsn(Opcodes.GOTO, loopLabel);
        functionCodegen.getMv().visitLabel(exitLoopLabel);
        functionCodegen.getMv().visitFrame(Opcodes.F_NEW,
                currentStackFrame.numLocals, currentStackFrame.locals,
                currentStackFrame.numStack, currentStackFrame.stack
        );
    }
}