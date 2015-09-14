/*
 *  Copyright  2015 Google Inc. All Rights Reserved.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.blockly.control;

import android.support.v4.util.SimpleArrayMap;

import com.google.blockly.model.Block;
import com.google.blockly.model.Connection;
import com.google.blockly.model.Field;
import com.google.blockly.model.Input;
import com.google.blockly.model.NameManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks information about the Workspace that we want fast access to.
 */
public class WorkspaceStats {

    // Lists of connections separated by type and ordered by y position.  This is optimized
    // for quickly finding the nearest connection when dragging a block around.
    private final List<Connection> mPreviousConnections = new ArrayList<>();
    private final List<Connection> mNextConnections = new ArrayList<>();
    private final List<Connection> mInputConnections = new ArrayList<>();
    private final List<Connection> mOutputConnections = new ArrayList<>();

    // Maps from variable/procedure names to the blocks/fields where they are referenced.
    private final SimpleArrayMap<String, List<Field.FieldVariable>> mVariableReferences =
            new SimpleArrayMap<>();
    private final NameManager mVariableNameManager;
    private final ProcedureManager mProcedureManager;


    public NameManager getVariableNameManager() {
        return mVariableNameManager;
    }

    public SimpleArrayMap<String, List<Field.FieldVariable>> getVariableReferences() {
        return mVariableReferences;
    }

    public List<Connection> getOutputConnections() {
        return mOutputConnections;
    }

    public List<Connection> getInputConnections() {
        return mInputConnections;
    }

    public List<Connection> getNextConnections() {
        return mNextConnections;
    }

    public List<Connection> getPreviousConnections() {
        return mPreviousConnections;
    }


    public WorkspaceStats(NameManager variableManager, ProcedureManager procedureManager) {
        mVariableNameManager = variableManager;
        mProcedureManager = procedureManager;
    }

    /**
     * Walks through a block and records all Connections, variable references, procedure
     * definitions and procedure calls.
     *
     * @param block The block to inspect.
     * @param recursive Whether to recursively collect stats for all descendants of the current
     *                  block.
     */
    public void collectStats(Block block, boolean recursive) {
        for (int i = 0; i < block.getInputs().size(); i++) {
            Input in = block.getInputs().get(i);
            addConnection(in.getConnection(), recursive);

            // Variables and references to them.
            for (int j = 0; j < in.getFields().size(); j++) {
                Field field = in.getFields().get(i);
                if (field.getType() == Field.TYPE_VARIABLE) {
                    Field.FieldVariable var = (Field.FieldVariable) field;
                    if (mVariableReferences.containsKey(var.getVariable())) {
                        mVariableReferences.get(var.getVariable()).add(var);
                    } else {
                        List<Field.FieldVariable> references = new ArrayList<>();
                        references.add(var);
                        mVariableReferences.put(var.getVariable(), references);
                    }
                    mVariableNameManager.addName(var.getVariable());
                }
            }
        }

        addConnection(block.getNextConnection(), recursive);
        // Don't recurse on outputs or previous connections--that's effectively walking back up the
        // tree.
        addConnection(block.getPreviousConnection(), false);
        addConnection(block.getOutputConnection(), false);

        // Procedures
        if (mProcedureManager.isProcedureDefinition(block)) {
            mProcedureManager.addProcedureDefinition(block);
        }
        // TODO (fenichel): Procedure calls will only work when mutations work.
        // The mutation will change the name of the block.  I believe that means name field,
        // not type.
        if (mProcedureManager.isProcedureReference(block)) {
            mProcedureManager.addProcedureReference(block);
        }
    }

    /**
     * Clear all of the internal state of this object.
     */
    public void clear() {
        mVariableReferences.clear();
        mProcedureManager.clear();
        mVariableNameManager.clearUsedNames();

        mPreviousConnections.clear();
        mNextConnections.clear();
        mOutputConnections.clear();
        mInputConnections.clear();
    }

    public void removeConnection(Connection conn, boolean recursive) {
        // TODO(fenichel): Implement in next CL.
    }

    public void removeBlock(Block block) {
        for (int i = 0; i < block.getInputs().size(); i++) {
            Input in = block.getInputs().get(i);
            removeConnection(in.getConnection(), true);

            // Variables and references to them.
            for (int j = 0; j < in.getFields().size(); j++) {
                Field field = in.getFields().get(i);
                if (field.getType() == Field.TYPE_VARIABLE) {
                    Field.FieldVariable var = (Field.FieldVariable) field;
                    if (mVariableReferences.containsKey(var.getVariable())) {
                        mVariableReferences.get(var.getVariable()).add(var);
                    } else {
                        List<Field.FieldVariable> references = new ArrayList<>();
                        references.add(var);
                        mVariableReferences.put(var.getVariable(), references);
                    }
                    mVariableNameManager.addName(var.getVariable());
                }
            }
        }

        removeConnection(block.getNextConnection(), false);
        // Don't recurse on outputs or previous connections--that's effectively walking back up the
        // tree.
        removeConnection(block.getPreviousConnection(), false);
        removeConnection(block.getOutputConnection(), false);

        // Procedures
        if (mProcedureManager.isProcedureDefinition(block)) {
            List<Block> children = mProcedureManager.removeProcedureDefinition(block);
            for (int i = 0; i < children.size(); i++) {
                removeBlock(children.get(i));
            }
        }
        // TODO (fenichel): Procedure calls will only work when mutations work.
        // The mutation will change the name of the block.  I believe that means name field,
        // not type.
        if (mProcedureManager.isProcedureReference(block)) {
            mProcedureManager.removeProcedureReference(block);
        }
    }

    // Do a binary search to find the right place to add the connection.
    private void addConnectionToList(Connection conn, List<Connection> list) {
        int pointerMin = 0;
        int pointerMax = list.size();
        while (pointerMin < pointerMax) {
            int pointerMid = (pointerMin + pointerMax) / 2;
            if (list.get(pointerMid).getPosition().y < conn.getPosition().y) {
                pointerMin = pointerMid + 1;
            } else if (list.get(pointerMid).getPosition().y > conn.getPosition().y) {
                pointerMax = pointerMid;
            } else {
                pointerMin = pointerMid;
                break;
            }
        }
        list.add(pointerMin, conn);
    }

    private void addConnection(Connection conn, boolean recursive) {
        if (conn != null) {
            switch (conn.getType()) {
                case Connection.CONNECTION_TYPE_INPUT:
                    addConnectionToList(conn, mInputConnections);
                    break;
                case Connection.CONNECTION_TYPE_OUTPUT:
                    addConnectionToList(conn, mOutputConnections);
                    break;
                case Connection.CONNECTION_TYPE_NEXT:
                    addConnectionToList(conn, mNextConnections);
                    break;
                case Connection.CONNECTION_TYPE_PREVIOUS:
                    addConnectionToList(conn, mPreviousConnections);
                    break;
                default:
                    throw new IllegalArgumentException("Found unknown connection type.");
            }
            if (recursive) {
                Block recursiveTarget = conn.getTargetBlock();
                if (recursiveTarget != null) {
                    collectStats(recursiveTarget, true);
                }
            }
        }
    }
}
