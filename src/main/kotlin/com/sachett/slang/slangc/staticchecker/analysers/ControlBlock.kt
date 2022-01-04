package com.sachett.slang.slangc.staticchecker.analysers

import com.sachett.slang.slangc.symbol.FunctionSymbol

class ControlBlock(
    override val parentFnSymbol: FunctionSymbol,
    val type: ControlBlockType = ControlBlockType.IF
) :
    IFunctionInnerBlock {
    private var doesReturnComputed = false

    override val children: ArrayList<IFunctionInnerBlock> = arrayListOf()

    override var doesReturnProperly: Boolean = false
        get() {
            if (doesReturnComputed) {
                return field
            }

            calculateReturn()
            doesReturnComputed = true
            return field
        }
        private set

    private fun calculateReturn() {
        if (children.size == 0) {
            doesReturnProperly = false
        }

        if (children.size == 1) {
            doesReturnProperly = children[0].doesReturnProperly
            return
        }

        for (child in children) {
            if ((child is IfControlNode) && !child.hasElseBlock) {
                continue
            }

            doesReturnProperly = doesReturnProperly || child.doesReturnProperly
        }
    }
}