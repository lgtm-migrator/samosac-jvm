package com.sachett.slang.slangc.compiler

import com.sachett.slang.logging.err
import com.sachett.slang.parser.SlangGrammarLexer
import com.sachett.slang.parser.SlangGrammarParser
import com.sachett.slang.slangc.staticchecker.StaticTypesChecker
import com.sachett.slang.slangc.symbol.symboltable.SymbolTable
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        err("slangc: No input files!")
    }

    val inputStream = CharStreams.fromFileName(args[0])
    val slangGrammarLexer = SlangGrammarLexer(inputStream)
    val commonTokenStream = CommonTokenStream(slangGrammarLexer)
    val slangParser = SlangGrammarParser(commonTokenStream)

    val programContext = slangParser.program()

    val symbolTable = SymbolTable()

    println("Visiting declarations...")
    val staticTypesChecker = StaticTypesChecker(symbolTable)
    staticTypesChecker.visit(programContext)

    println("Dumping symbol table: ")
    TODO("Symbol table dump to be implemented")
}