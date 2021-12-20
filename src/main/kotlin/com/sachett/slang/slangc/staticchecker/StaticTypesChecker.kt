package com.sachett.slang.slangc.staticchecker

import com.sachett.slang.logging.err
import com.sachett.slang.logging.fmtfatalerr
import com.sachett.slang.parser.SlangGrammarBaseVisitor
import com.sachett.slang.parser.SlangGrammarParser
import com.sachett.slang.slangc.symbol.*
import com.sachett.slang.slangc.symbol.symboltable.SymbolTable

class StaticTypesChecker(private val symbolTable: SymbolTable) : SlangGrammarBaseVisitor<Void>() {

    /* --------------------- Utility functions ----------------------- */

    private fun processArgList(argParamCtx: SlangGrammarParser.ArgParamContext): ISymbol {
        val idName = argParamCtx.IDENTIFIER().text
        val definedOnLineNum = argParamCtx.IDENTIFIER().symbol.line
        val typeNameCtx = argParamCtx.typeName()

        /**
         * We increment the scope once here and decrement it again after inserting the
         * symbols present in the parameter list.
         * Not creating a new scope on the next scope increment (when it enters the block)
         * preserves the parameters that have been inserted already into the function's scope.
         * The decrement is necessary because when the compiler enters the block, it will
         * increment its scope again. We want the parameters to stay.
         */

        symbolTable.incrementScope()

        var symbol: ISymbol? = null

        if (typeNameCtx.INTTYPE() != null) {
            val intSymbol = IntSymbol(idName, definedOnLineNum)
            symbolTable.insert(idName, intSymbol)
            symbol = intSymbol
        } else if (typeNameCtx.STRINGTYPE() != null) {
            val stringSymbol = StringSymbol(idName, definedOnLineNum)
            symbolTable.insert(idName, stringSymbol)
            symbol = stringSymbol
        } else if (typeNameCtx.BOOLTYPE() != null) {
            val boolSymbol = BoolSymbol(idName, definedOnLineNum)
            symbolTable.insert(idName, boolSymbol)
            symbol = boolSymbol
        } else if (typeNameCtx.VOIDTYPE() != null) {
            err(
                "[Error, Line ${definedOnLineNum}] Void type variables are not supported. " +
                        "What did you expect though...?"
            )
        }

        symbolTable.decrementScope(false)
        return symbol!!
    }

    private fun parseAndAddFunctionParamsExplicitDef(
        ctx: SlangGrammarParser.ExplicitRetTypeFuncDefContext
    ): ArrayList<ISymbol> {
        val paramList: ArrayList<ISymbol> = arrayListOf()

        ctx.funcArgList().args.forEach {
            paramList.add(processArgList(it))
        }

        return paramList
    }

    private fun parseAndAddFunctionParamsImplicitDef(
        ctx: SlangGrammarParser.ExplicitRetTypeFuncDefContext
    ): ArrayList<ISymbol> {
        val paramList: ArrayList<ISymbol> = arrayListOf()

        ctx.funcArgList().args.forEach {
            paramList.add(processArgList(it))
        }

        return paramList
    }

    /* -----------------  Visitor methods -------------------- */

    override fun visitProgram(ctx: SlangGrammarParser.ProgramContext?): Void {
        println("Visiting program...")
        return super.visitProgram(ctx)
    }

    override fun visitBlock(ctx: SlangGrammarParser.BlockContext?): Void {
        println("Visiting block...")
        symbolTable.incrementScope()
        val blockVisit = super.visitBlock(ctx)
        symbolTable.decrementScope()
        return blockVisit
    }

    // TODO: refactor out existing symbol checks in the following functions

    override fun visitDeclStmt(ctx: SlangGrammarParser.DeclStmtContext?): Void {
        println("Visiting DeclStmt...") // debug
        val idName = ctx!!.IDENTIFIER().symbol.text
        val firstAppearedLineNum = ctx.IDENTIFIER().symbol.line
        val typeNameCtx = ctx.typeName()

        val existingSymbol = symbolTable.lookup(idName)

        if (existingSymbol != null) {
            fmtfatalerr(
                "Identifier $idName was declared before on line ${existingSymbol.firstAppearedLine}.",
                firstAppearedLineNum
            )
        }

        if (typeNameCtx.BOOLTYPE() != null) {
            println("Found boolie type id $idName")
            val boolSymbol = BoolSymbol(idName, firstAppearedLineNum)
            symbolTable.insert(idName, boolSymbol)
        } else if (typeNameCtx.INTTYPE() != null) {
            println("Found int type for id $idName")
            val intSymbol = IntSymbol(idName, firstAppearedLineNum)
            symbolTable.insert(idName, intSymbol)
        } else if (typeNameCtx.STRINGTYPE() != null) {
            println("Found string type for id $idName")
            val stringSymbol = StringSymbol(idName, firstAppearedLineNum)
            symbolTable.insert(idName, stringSymbol)
        } else if (typeNameCtx.VOIDTYPE() != null) {
            // We do not have void variables
            fmtfatalerr("Void types for variable declarations are not allowed.", firstAppearedLineNum)
        }

        return super.visitDeclStmt(ctx)
    }

    override fun visitNormalDeclAssignStmt(ctx: SlangGrammarParser.NormalDeclAssignStmtContext?): Void {
        println("Visiting NormalDeclAssignStmt...") // debug
        val idName = ctx!!.IDENTIFIER().symbol.text
        val firstAppearedLineNum = ctx.IDENTIFIER().symbol.line
        val typeNameCtx = ctx.typeName()

        val existingSymbol = symbolTable.lookup(idName)

        if (existingSymbol != null) {
            fmtfatalerr(
                "Identifier $idName was declared before on line ${existingSymbol.firstAppearedLine}.",
                firstAppearedLineNum
            )
        }

        if (typeNameCtx.BOOLTYPE() != null) {
            // this should not happen here
            // it's here just for the sake of completeness
            fmtfatalerr("Illegal boolie declaration.", firstAppearedLineNum)
        }

        if (typeNameCtx.INTTYPE() != null) {
            val intSymbol = IntSymbol(idName, firstAppearedLineNum)
            val intExprChecker = IntExpressionChecker(symbolTable)

            if (!intExprChecker.checkExpr(ctx.expr())) {
                val typeDetector = ExpressionTypeDetector(symbolTable)
                val detectedType = typeDetector.getType(ctx.expr())
                fmtfatalerr(
                    "Expected ${SymbolType.INT.asString} expression on RHS, " +
                            "found ${if (detectedType.first) detectedType.second.asString else "mismatched types. "}. ",
                    firstAppearedLineNum
                )
            }

            symbolTable.insert(idName, intSymbol)
        } else if (typeNameCtx.STRINGTYPE() != null) {
            val stringSymbol = StringSymbol(idName, firstAppearedLineNum)
            val stringExprChecker = StringExpressionChecker(symbolTable)

            if (!stringExprChecker.checkExpr(ctx.expr())) {
                val typeDetector = ExpressionTypeDetector(symbolTable)
                val detectedType = typeDetector.getType(ctx.expr())
                fmtfatalerr(
                    "Expected ${SymbolType.STRING.asString} expression on RHS, " +
                            "found ${if (detectedType.first) detectedType.second.asString else "mismatched types. "}. ",
                    firstAppearedLineNum
                )
            }

            symbolTable.insert(idName, stringSymbol)
        } else if (typeNameCtx.VOIDTYPE() != null) {
            // no support for void variables
            fmtfatalerr("Void types for variable declarations are not yet supported. ", firstAppearedLineNum)
        }

        return super.visitNormalDeclAssignStmt(ctx)
    }

    override fun visitBooleanDeclAssignStmt(ctx: SlangGrammarParser.BooleanDeclAssignStmtContext?): Void {
        println("Visiting BooleanDeclAssign...") // debug
        val idName = ctx!!.IDENTIFIER().symbol.text
        val firstAppearedLineNum = ctx.IDENTIFIER().symbol.line

        val existingSymbol = symbolTable.lookup(idName)

        if (existingSymbol != null) {
            fmtfatalerr(
                "Identifier $idName was declared before on line ${existingSymbol.firstAppearedLine}.",
                firstAppearedLineNum
            )
        }

        val boolSymbol = BoolSymbol(idName, firstAppearedLineNum)
        val boolExprChecker = BoolExpressionChecker(symbolTable)

        if (!boolExprChecker.checkExpr(ctx.booleanExpr())) {
            fmtfatalerr(
                "Expected ${SymbolType.BOOL.asString} expression on RHS, found mismatched types.",
                firstAppearedLineNum
            )
        }

        symbolTable.insert(idName, boolSymbol)
        return super.visitBooleanDeclAssignStmt(ctx)
    }

    override fun visitTypeInferredDeclAssignStmt(ctx: SlangGrammarParser.TypeInferredDeclAssignStmtContext?): Void {
        println("Visiting TypeInferredDeclAssignStmt...") // debug
        val idName = ctx!!.IDENTIFIER().symbol.text
        val firstAppearedLineNum = ctx.IDENTIFIER().symbol.line

        val existingSymbol = symbolTable.lookup(idName)

        if (existingSymbol != null) {
            fmtfatalerr(
                "Identifier $idName was declared before on line ${existingSymbol.firstAppearedLine}.",
                firstAppearedLineNum
            )
        }

        val expressionTypeDetector = ExpressionTypeDetector(symbolTable)
        val (homoTypes, expressionType) = expressionTypeDetector.getType(ctx.expr())

        if (!homoTypes) {
            fmtfatalerr(
                "Incompatible types in expression on RHS. All terms should be on same type in the expression in the RHS.",
                firstAppearedLineNum
            )
        }

        when (expressionType) {
            SymbolType.INT -> {
                val intSymbol = IntSymbol(idName, firstAppearedLineNum, true)
                val intExprChecker = IntExpressionChecker(symbolTable)

                if (!intExprChecker.checkExpr(ctx.expr())) {
                    val typeDetector = ExpressionTypeDetector(symbolTable)
                    val detectedType = typeDetector.getType(ctx.expr())
                    fmtfatalerr(
                        "While inferring type, expected ${SymbolType.INT.asString} expression on RHS, " +
                                "found ${if (detectedType.first) detectedType.second.asString else "mismatched types. "}. ",
                        firstAppearedLineNum
                    )
                }

                symbolTable.insert(idName, intSymbol)
            }
            SymbolType.STRING -> {
                val stringSymbol = StringSymbol(idName, firstAppearedLineNum, true)
                val stringExprChecker = StringExpressionChecker(symbolTable)

                if (!stringExprChecker.checkExpr(ctx.expr())) {
                    val typeDetector = ExpressionTypeDetector(symbolTable)
                    val detectedType = typeDetector.getType(ctx.expr())
                    fmtfatalerr(
                        "While inferring type, expected ${SymbolType.STRING.asString} expression on RHS, " +
                                "found ${if (detectedType.first) detectedType.second.asString else "mismatched types. "}. ",
                        firstAppearedLineNum
                    )
                }

                symbolTable.insert(idName, stringSymbol)
            }
            SymbolType.BOOL -> {
                // We probably have one of these 2 cases here:
                // id = id1; - both should be boolean type
                // id = () -> functionCall; - functionCall should return bool
                // Because this syntax is common for both expr and booleanExpr,
                // they will be parsed as normal expression instead of booleanExpr

                // Sadly, we cannot verify this here because BoolExpressionChecker
                // takes a different kind (type) of ctx argument
                // TODO: implement the original override for checkExpr that takes a normal expression context in BoolExpressionChecker

                val boolSymbol = BoolSymbol(idName, firstAppearedLineNum, true)
                symbolTable.insert(idName, boolSymbol)
            }
            else -> {
                // invalid type
                fmtfatalerr(
                    "Invalid type (${expressionType.asString} on RHS for assignment to an identifier. ",
                    firstAppearedLineNum
                )
            }
        }

        return super.visitTypeInferredDeclAssignStmt(ctx)
    }

    override fun visitTypeInferredBooleanDeclAssignStmt(ctx: SlangGrammarParser.TypeInferredBooleanDeclAssignStmtContext?): Void {
        println("Visiting TypeInferredBooleanDeclAssign...") // debug
        val idName = ctx!!.IDENTIFIER().symbol.text
        val firstAppearedLineNum = ctx.IDENTIFIER().symbol.line

        val existingSymbol = symbolTable.lookup(idName)

        if (existingSymbol != null) {
            fmtfatalerr(
                "Identifier $idName was declared before on line ${existingSymbol.firstAppearedLine}.",
                firstAppearedLineNum
            )
        }

        val boolSymbol = BoolSymbol(idName, firstAppearedLineNum, true)
        val boolExprChecker = BoolExpressionChecker(symbolTable)

        if (!boolExprChecker.checkExpr(ctx.booleanExpr())) {
            fmtfatalerr(
                "While inferring type, expected ${SymbolType.BOOL.asString} expression on RHS, found mismatched types.",
                firstAppearedLineNum
            )
        }

        symbolTable.insert(idName, boolSymbol)

        return super.visitTypeInferredBooleanDeclAssignStmt(ctx)
    }

    override fun visitExprAssign(ctx: SlangGrammarParser.ExprAssignContext?): Void {
        return super.visitExprAssign(ctx)
    }

    override fun visitExprIdentifier(ctx: SlangGrammarParser.ExprIdentifierContext?): Void {
        return super.visitExprIdentifier(ctx)
    }

    override fun visitFunctionCallWithArgs(ctx: SlangGrammarParser.FunctionCallWithArgsContext?): Void {
        return super.visitFunctionCallWithArgs(ctx)
    }

    override fun visitFunctionCallNoArgs(ctx: SlangGrammarParser.FunctionCallNoArgsContext?): Void {
        return super.visitFunctionCallNoArgs(ctx)
    }

    override fun visitBooleanExprAssign(ctx: SlangGrammarParser.BooleanExprAssignContext?): Void {
        return super.visitBooleanExprAssign(ctx)
    }

    override fun visitImplicitRetTypeFuncDef(ctx: SlangGrammarParser.ImplicitRetTypeFuncDefContext?): Void {
        return super.visitImplicitRetTypeFuncDef(ctx)
    }

    override fun visitExplicitRetTypeFuncDef(ctx: SlangGrammarParser.ExplicitRetTypeFuncDefContext?): Void {
        return super.visitExplicitRetTypeFuncDef(ctx)
    }

    override fun visitIfStmt(ctx: SlangGrammarParser.IfStmtContext?): Void {
        val boolExprChecker = BoolExpressionChecker(symbolTable)
        if (!boolExprChecker.checkExpr(ctx!!.booleanExpr())) {
            fmtfatalerr("Invalid boolean expression in condition required " +
                    "for if statement.", ctx.IF().symbol.line)
        }
        return super.visitIfStmt(ctx)
    }

    override fun visitWhileStmt(ctx: SlangGrammarParser.WhileStmtContext?): Void {
        val boolExprChecker = BoolExpressionChecker(symbolTable)
        if (!boolExprChecker.checkExpr(ctx!!.booleanExpr())) {
            fmtfatalerr("Invalid boolean expression in condition required " +
                    "for while statement.", ctx.WHILE().symbol.line)
        }
        return super.visitWhileStmt(ctx)
    }
}