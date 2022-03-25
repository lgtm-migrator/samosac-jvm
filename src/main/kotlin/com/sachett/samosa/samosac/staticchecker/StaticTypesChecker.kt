package com.sachett.samosa.samosac.staticchecker

import com.sachett.samosa.logging.err
import com.sachett.samosa.logging.fmtfatalerr
import com.sachett.samosa.parser.SamosaBaseVisitor
import com.sachett.samosa.parser.SamosaParser
import com.sachett.samosa.samosac.staticchecker.analyzers.FunctionControlPathAnalyzer
import com.sachett.samosa.samosac.staticchecker.evaluators.BoolExpressionEvaluator
import com.sachett.samosa.samosac.staticchecker.evaluators.IntExpressionEvaluator
import com.sachett.samosa.samosac.staticchecker.evaluators.StringExpressionEvaluator
import com.sachett.samosa.samosac.symbol.*
import com.sachett.samosa.samosac.symbol.symboltable.SymbolTable
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ErrorNodeImpl

class StaticTypesChecker(private val symbolTable: SymbolTable) : SamosaBaseVisitor<Void?>() {

    /* --------------------- Utility functions ----------------------- */

    private fun processArgList(argParamCtx: SamosaParser.ArgParamContext): ISymbol {
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
            val intSymbol = IntSymbol(idName, definedOnLineNum, isInitialValueCalculated = true, initializeExpressionPresent = false)
            symbolTable.insert(idName, intSymbol)
            symbol = intSymbol
        } else if (typeNameCtx.STRINGTYPE() != null) {
            val stringSymbol = StringSymbol(idName, definedOnLineNum, isInitialValueCalculated = true, initializeExpressionPresent = false)
            symbolTable.insert(idName, stringSymbol)
            symbol = stringSymbol
        } else if (typeNameCtx.BOOLTYPE() != null) {
            val boolSymbol = BoolSymbol(idName, definedOnLineNum, isInitialValueCalculated = true, initializeExpressionPresent = false)
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

    /**
     * Parses and adds the function parameters for a function definition
     * in which the return type is explicitly declared.
     */
    private fun parseAndAddFunctionParamsExplicitDef(
        ctx: SamosaParser.ExplicitRetTypeFuncDefContext
    ): ArrayList<ISymbol> {
        val paramList: ArrayList<ISymbol> = arrayListOf()

        ctx.funcArgList().args.forEach {
            paramList.add(processArgList(it))
        }

        return paramList
    }

    /**
     * Parses and adds the function parameters for a function definition
     * in which the return type is implicitly void.
     */
    private fun parseAndAddFunctionParamsImplicitDef(
        ctx: SamosaParser.ImplicitRetTypeFuncDefContext
    ): ArrayList<ISymbol> {
        val paramList: ArrayList<ISymbol> = arrayListOf()

        ctx.funcArgList().args.forEach {
            paramList.add(processArgList(it))
        }

        return paramList
    }

    /* -----------------  Visitor methods -------------------- */

    override fun visitProgram(ctx: SamosaParser.ProgramContext?): Void? {
        println("Visiting program...")
        return super.visitProgram(ctx)
    }

    override fun visitBlock(ctx: SamosaParser.BlockContext?): Void? {
        println("Visiting block...")
        symbolTable.incrementScope()
        val blockVisit = super.visitBlock(ctx)

        val blockStart = Pair(ctx!!.start.line, ctx.start.charPositionInLine)
        symbolTable.registerBlockInCurrentCoordinates(blockStart)

        symbolTable.decrementScope()
        return blockVisit
    }

    // TODO: refactor out existing symbol checks in the following functions

    override fun visitDeclStmt(ctx: SamosaParser.DeclStmtContext?): Void? {
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
            // isInitialValueCalculated is true here because the var is initialized to a default value
            val boolSymbol = BoolSymbol(idName, firstAppearedLineNum, isInitialValueCalculated = true, initializeExpressionPresent = false)
            symbolTable.insert(idName, boolSymbol)
        } else if (typeNameCtx.INTTYPE() != null) {
            println("Found int type for id $idName")
            val intSymbol = IntSymbol(idName, firstAppearedLineNum, isInitialValueCalculated = true, initializeExpressionPresent = false)
            symbolTable.insert(idName, intSymbol)
        } else if (typeNameCtx.STRINGTYPE() != null) {
            println("Found string type for id $idName")
            val stringSymbol = StringSymbol(idName, firstAppearedLineNum, isInitialValueCalculated = true, initializeExpressionPresent = false)
            symbolTable.insert(idName, stringSymbol)
        } else if (typeNameCtx.VOIDTYPE() != null) {
            // We do not have void variables
            fmtfatalerr("Void types for variable declarations are not allowed.", firstAppearedLineNum)
        }

        return super.visitDeclStmt(ctx)
    }

    override fun visitNormalDeclAssignStmt(ctx: SamosaParser.NormalDeclAssignStmtContext?): Void? {
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
            val intSymbol = IntSymbol(idName, firstAppearedLineNum, isInitialValueCalculated = false, initializeExpressionPresent = true)
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

            val intExpressionEvaluator = IntExpressionEvaluator(ctx.expr())
            intSymbol.value = intExpressionEvaluator.evaluate()
            if (intExpressionEvaluator.checkStaticEvaluable()) {
                intSymbol.isInitialValueCalculated = true
            }

            symbolTable.insert(idName, intSymbol)
        } else if (typeNameCtx.STRINGTYPE() != null) {
            val stringSymbol = StringSymbol(idName, firstAppearedLineNum, isInitialValueCalculated = false, initializeExpressionPresent = true)
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

            val stringExpressionEvaluator = StringExpressionEvaluator(ctx.expr())
            stringSymbol.value = stringExpressionEvaluator.evaluate()
            if (stringExpressionEvaluator.checkStaticEvaluable()) {
                stringSymbol.isInitialValueCalculated = true
            }

            symbolTable.insert(idName, stringSymbol)
        } else if (typeNameCtx.VOIDTYPE() != null) {
            // no support for void variables
            fmtfatalerr("Void types for variable declarations are not yet supported. ", firstAppearedLineNum)
        }

        return super.visitNormalDeclAssignStmt(ctx)
    }

    override fun visitBooleanDeclAssignStmt(ctx: SamosaParser.BooleanDeclAssignStmtContext?): Void? {
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

        val boolSymbol = BoolSymbol(idName, firstAppearedLineNum, isInitialValueCalculated = false, initializeExpressionPresent = true)
        val boolExprChecker = BoolExpressionChecker(symbolTable)

        if (!boolExprChecker.checkExpr(ctx.booleanExpr())) {
            fmtfatalerr(
                "Expected ${SymbolType.BOOL.asString} expression on RHS, found mismatched types.",
                firstAppearedLineNum
            )
        }

        val boolExpressionEvaluator = BoolExpressionEvaluator(ctx.booleanExpr(), symbolTable)
        boolSymbol.value = boolExpressionEvaluator.evaluate()
        if (boolExpressionEvaluator.checkStaticEvaluable()) {
            boolSymbol.isInitialValueCalculated = true
        }
        symbolTable.insert(idName, boolSymbol)

        return super.visitBooleanDeclAssignStmt(ctx)
    }

    override fun visitTypeInferredDeclAssignStmt(ctx: SamosaParser.TypeInferredDeclAssignStmtContext?): Void? {
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
                val intSymbol = IntSymbol(idName, firstAppearedLineNum, true, isInitialValueCalculated = false, initializeExpressionPresent = true)
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

                val intExpressionEvaluator = IntExpressionEvaluator(ctx.expr())
                intSymbol.value = intExpressionEvaluator.evaluate()
                if (intExpressionEvaluator.checkStaticEvaluable()) {
                    intSymbol.isInitialValueCalculated = true
                }
                symbolTable.insert(idName, intSymbol)
            }
            SymbolType.STRING -> {
                val stringSymbol = StringSymbol(idName, firstAppearedLineNum, true, isInitialValueCalculated = false, initializeExpressionPresent = true)
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

                val stringExpressionEvaluator = StringExpressionEvaluator(ctx.expr())
                stringSymbol.value = stringExpressionEvaluator.evaluate()
                if (stringExpressionEvaluator.checkStaticEvaluable()) {
                    stringSymbol.isInitialValueCalculated = true
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

                val boolSymbol = BoolSymbol(idName, firstAppearedLineNum, true, isInitialValueCalculated = false, initializeExpressionPresent = true)
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

    override fun visitTypeInferredBooleanDeclAssignStmt(ctx: SamosaParser.TypeInferredBooleanDeclAssignStmtContext?): Void? {
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

        val boolSymbol = BoolSymbol(idName, firstAppearedLineNum, true, isInitialValueCalculated = false, initializeExpressionPresent = true)
        val boolExprChecker = BoolExpressionChecker(symbolTable)

        if (!boolExprChecker.checkExpr(ctx.booleanExpr())) {
            fmtfatalerr(
                "While inferring type, expected ${SymbolType.BOOL.asString} expression on RHS, found mismatched types.",
                firstAppearedLineNum
            )
        }

        val boolExpressionEvaluator = BoolExpressionEvaluator(ctx.booleanExpr(), symbolTable)
        boolSymbol.value = boolExpressionEvaluator.evaluate()
        if (boolExpressionEvaluator.checkStaticEvaluable()) {
            boolSymbol.isInitialValueCalculated = true
        }
        symbolTable.insert(idName, boolSymbol)

        return super.visitTypeInferredBooleanDeclAssignStmt(ctx)
    }

    override fun visitExprAssign(ctx: SamosaParser.ExprAssignContext?): Void? {
        println("Visiting ExprAssignStmt...") // debug
        val idName = ctx!!.IDENTIFIER().symbol.text
        val lineNum = ctx.IDENTIFIER().symbol.line

        val existingSymbol = symbolTable.lookup(idName)
            ?: fmtfatalerr(
                "Assignment to previously undeclared symbol.",
                lineNum
            )

        when (existingSymbol.symbolType) {
            SymbolType.INT -> {
                val intExprChecker = IntExpressionChecker(symbolTable)

                if (!intExprChecker.checkExpr(ctx.expr())) {
                    val typeDetector = ExpressionTypeDetector(symbolTable)
                    val detectedType = typeDetector.getType(ctx.expr())
                    fmtfatalerr(
                        "Expected ${SymbolType.INT.asString} expression on RHS, " +
                                "found ${if (detectedType.first) detectedType.second.asString else "mismatched types. "}. ",
                        lineNum
                    )
                }
            }
            SymbolType.STRING -> {
                val stringExprChecker = StringExpressionChecker(symbolTable)

                if (!stringExprChecker.checkExpr(ctx.expr())) {
                    val typeDetector = ExpressionTypeDetector(symbolTable)
                    val detectedType = typeDetector.getType(ctx.expr())
                    fmtfatalerr(
                        "Expected ${SymbolType.STRING.asString} expression on RHS, " +
                                "found ${if (detectedType.first) detectedType.second.asString else "mismatched types. "}. ",
                        lineNum
                    )
                }
            }
            SymbolType.BOOL -> {
                // We probably have one of these 2 cases here:
                // id = id1; - both should be boolean type
                // id = () -> functionCall; - functionCall should return bool
                // Because this syntax is common for both expr and booleanExpr,
                // they will be parsed as normal expression instead of booleanExpr

                // Sadly, we cannot verify this using BoolExpressionChecker here
                // because BoolExpressionChecker takes a different kind (type)
                // of ctx argument.
                // TODO: use the original override for checkExpr that takes a normal expression context in BoolExpressionChecker (if implemented)

                // So instead, we use our ExpressionTypeDetector.
                val expressionTypeDetector = ExpressionTypeDetector(symbolTable)
                val (homoTypes, rhsExprRetType) = expressionTypeDetector.getType(ctx.expr())

                if (!homoTypes) {
                    fmtfatalerr("Mismatched types on RHS.", lineNum)
                }

                if (rhsExprRetType != SymbolType.BOOL) {
                    fmtfatalerr(
                        "Invalid assignment: type mismatch of identifier and expression. " +
                                "Expected an expression that evaluates to ${SymbolType.BOOL.asString} on RHS. ", lineNum
                    )
                }
            }
            else -> {
                // invalid type
                fmtfatalerr(
                    "Cannot assign to an identifier of type ${existingSymbol.symbolType}. ",
                    lineNum
                )
            }
        }

        return super.visitExprAssign(ctx)
    }

    override fun visitExprIdentifier(ctx: SamosaParser.ExprIdentifierContext?): Void? {
        println("Visiting ExprIdentifier...") // debug
        val idName = ctx!!.IDENTIFIER().symbol.text
        val lineNum = ctx.IDENTIFIER().symbol.line

        symbolTable.lookup(idName)
            ?: fmtfatalerr(
                "Unknown identifier $idName.",
                lineNum
            )

        return super.visitExprIdentifier(ctx)
    }

    override fun visitFunctionCallWithArgs(ctx: SamosaParser.FunctionCallWithArgsContext?): Void? {
        // we don't have any use for the return type of the function call
        // we use this just for checking the function call
        FunctionCallExprChecker.getRetTypeOfFunctionCallWithArgs(ctx, symbolTable)
        return super.visitFunctionCallWithArgs(ctx)
    }

    override fun visitFunctionCallNoArgs(ctx: SamosaParser.FunctionCallNoArgsContext?): Void? {
        // we don't have any use for the return type of the function call
        // we use this just for checking the function call
        FunctionCallExprChecker.getRetTypeOfFunctionCallNoArgs(ctx, symbolTable)
        return super.visitFunctionCallNoArgs(ctx)
    }

    override fun visitBooleanExprAssign(ctx: SamosaParser.BooleanExprAssignContext?): Void? {
        // check both sides and see if they're boolean types

        // left side should have a boolean identifier

        println("Visiting BooleanExprAssign...") // debug
        val idName = ctx!!.IDENTIFIER().symbol.text
        val lineNum = ctx.IDENTIFIER().symbol.line

        val existingSymbol = symbolTable.lookup(idName)
            ?: fmtfatalerr(
                "Unknown identifier $idName.",
                lineNum
            )

        if (existingSymbol.symbolType != SymbolType.BOOL) {
            fmtfatalerr(
                "Unexpected type found on left side of assignment: " +
                        "expected a ${SymbolType.BOOL.asString} identifier " +
                        "but found an identifier of type ${existingSymbol.symbolType.asString}.",
                lineNum
            )
        }

        // right side should be an expression that returns a boolean value
        val boolExpressionChecker = BoolExpressionChecker(symbolTable)

        if (!boolExpressionChecker.checkExpr(ctx.booleanExpr())) {
            fmtfatalerr(
                "Unexpected expression type on right side of assignment. Expected boolean expression. ",
                lineNum
            )
        }

        return super.visitBooleanExprAssign(ctx)
    }

    override fun visitImplicitRetTypeFuncDef(ctx: SamosaParser.ImplicitRetTypeFuncDefContext?): Void? {
        val idName = ctx!!.IDENTIFIER().symbol.text
        val definedLineNum = ctx.IDENTIFIER().symbol.line

        val existingSymbol = symbolTable.lookup(idName)
        val isBuiltin = symbolTable.lookupBuiltinFunction(idName, null);

        if (existingSymbol != null) {
            fmtfatalerr(
                "Identifier $idName was declared before on line ${existingSymbol.firstAppearedLine}.",
                definedLineNum
            )
        }

        if (isBuiltin != null) {
            fmtfatalerr(
                "$idName is a built-in function. You do not have the power to redefine it. Sad.",
                definedLineNum
            )
        }

        val paramList = parseAndAddFunctionParamsImplicitDef(ctx)
        val functionSymbol = FunctionSymbol(idName, definedLineNum, paramList, SymbolType.VOID)

        val functionReturnsChecker = FunctionReturnsChecker(symbolTable, functionSymbol)
        val visitFunctionInside = super.visitImplicitRetTypeFuncDef(ctx)
        val functionReturnsOk = functionReturnsChecker.checkReturnStmts(ctx)

        val functionControlPathsChecker = FunctionControlPathAnalyzer(symbolTable, functionSymbol)
        val functionReturnPathsOk = functionControlPathsChecker.checkAllControlPathsForReturns(ctx)

        if (!functionReturnPathsOk) {
            fmtfatalerr(
                "Not all possible execution paths in the function return a value of type ${functionSymbol.returnType.asString}.",
                definedLineNum
            )
        }

        if (!functionReturnsOk) {
            fmtfatalerr(
                "Function body contains errors. (There might be additional information above.)",
                definedLineNum
            )
        }

        symbolTable.insert(idName, functionSymbol)
        return visitFunctionInside
    }

    override fun visitExplicitRetTypeFuncDef(ctx: SamosaParser.ExplicitRetTypeFuncDefContext?): Void? {
        println("Visiting ExplicitRetTypeFuncDef")
        val idName = ctx!!.IDENTIFIER().symbol.text
        val definedLineNum = ctx.IDENTIFIER().symbol.line

        val existingSymbol = symbolTable.lookup(idName)
        val isBuiltin = symbolTable.lookupBuiltinFunction(idName, null);

        if (existingSymbol != null) {
            fmtfatalerr(
                "Identifier $idName was declared before on line ${existingSymbol.firstAppearedLine}.",
                definedLineNum
            )
        }

        if (isBuiltin != null) {
            fmtfatalerr(
                "$idName is a built-in function. You do not have the power to redefine it. Sad.",
                definedLineNum
            )
        }

        val paramList = parseAndAddFunctionParamsExplicitDef(ctx)

        val funcRetType = if (ctx.typeName().INTTYPE() != null) {
            SymbolType.INT
        } else if (ctx.typeName().STRINGTYPE() != null) {
            SymbolType.STRING
        } else if (ctx.typeName().BOOLTYPE() != null) {
            SymbolType.BOOL
        } else {
            SymbolType.VOID
        }

        val functionSymbol = FunctionSymbol(idName, definedLineNum, paramList, funcRetType)
        symbolTable.insert(idName, functionSymbol)
        val visitFunctionInside = super.visitExplicitRetTypeFuncDef(ctx)
        val functionReturnsChecker = FunctionReturnsChecker(symbolTable, functionSymbol)
        val functionReturnsOk = functionReturnsChecker.checkReturnStmts(ctx)

        val functionControlPathsChecker = FunctionControlPathAnalyzer(symbolTable, functionSymbol)
        val functionReturnPathsOk = functionControlPathsChecker.checkAllControlPathsForReturns(ctx)

        if (!functionReturnPathsOk) {
            fmtfatalerr(
                "Not all possible execution paths in the function return a value of type ${functionSymbol.returnType.asString}.",
                definedLineNum
            )
        }

        if (!functionReturnsOk) {
            fmtfatalerr(
                "Function body contains errors. (There might be additional information above.)",
                definedLineNum
            )
        }

        return visitFunctionInside
    }

    override fun visitIfStmt(ctx: SamosaParser.IfStmtContext?): Void? {
        val boolExprChecker = BoolExpressionChecker(symbolTable)
        val boolExprsInIfStmt = ctx!!.booleanExpr()

        // Check all the if conditions and else if conditions
        repeat (boolExprsInIfStmt.size) {
            if (!boolExprChecker.checkExpr(boolExprsInIfStmt[it])) {
                fmtfatalerr(
                    "Invalid boolean expression in condition " +
                            "for if statement.", ctx.IF(it).symbol.line
                )
            }
        }

        return super.visitIfStmt(ctx)
    }

    override fun visitWhileStmt(ctx: SamosaParser.WhileStmtContext?): Void? {
        val boolExprChecker = BoolExpressionChecker(symbolTable)
        if (!boolExprChecker.checkExpr(ctx!!.booleanExpr())) {
            fmtfatalerr(
                "Invalid boolean expression in condition " +
                        "for while statement.", ctx.WHILE().symbol.line
            )
        }
        return super.visitWhileStmt(ctx)
    }

    override fun visitBreakControlStmt(ctx: SamosaParser.BreakControlStmtContext?): Void? {
        var parentBlockCtx = ctx!!.parent

        while (parentBlockCtx != null && parentBlockCtx !is SamosaParser.WhileStmtContext) {
            parentBlockCtx = parentBlockCtx.parent
        }

        if (parentBlockCtx == null) {
            // this statement is not a part of while
            fmtfatalerr("Breakout statement must be within a loop.", ctx.BREAK().symbol.line)
        }

        return null
    }

    override fun visitContinueControlStmt(ctx: SamosaParser.ContinueControlStmtContext?): Void? {
        var parentBlockCtx = ctx!!.parent

        while (parentBlockCtx != null && parentBlockCtx !is SamosaParser.WhileStmtContext) {
            parentBlockCtx = parentBlockCtx.parent
        }

        if (parentBlockCtx == null) {
            // this statement is not a part of while
            fmtfatalerr("Continue statement must be within a loop.", ctx.CONTINUE().symbol.line)
        }

        return null
    }

    override fun visitErrorNode(node: ErrorNode?): Void? {
        // TODO: improve error handling
        val errorNode = node as ErrorNodeImpl
        val lineNumber = errorNode.symbol.line
        fmtfatalerr("Syntax error at '${errorNode.text}'", lineNumber)
    }

    override fun visitNeedsStmt(ctx: SamosaParser.NeedsStmtContext?): Void? {
        println("[Warning, Line ${ctx!!.start.line}] Needs statement is not yet supported. Will be coming soon!") // warning log
        return super.visitNeedsStmt(ctx)
    }
}