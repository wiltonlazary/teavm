/*
 *  Copyright 2016 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.samples.llvm;

public final class BinaryTrees {
    private BinaryTrees() {

    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 5; ++i) {
            System.out.println("Warm-up iteration #" + i);
            iteration();
        }
        long start = System.currentTimeMillis();
        for (int i = 0; i < 20; ++i) {
            System.out.println("Measure iteration #" + i);
            iteration();
        }
        long end = System.currentTimeMillis();
        System.out.println("Operation took " + (end - start) + " ms");
    }

    private static void iteration() {
        int n = 18;
        int minDepth = 4;
        int maxDepth = Math.max(minDepth + 2, n);
        int stretchDepth = maxDepth + 1;
        int check = (TreeNode.create(0, stretchDepth)).check();

        //System.out.println("stretch tree of depth " + (maxDepth + 1) + "\t check: " + check);

        TreeNode longLivedTree = TreeNode.create(0, maxDepth);
        for (int depth = minDepth; depth <= maxDepth; depth += 2) {
            int iterations = 1 << (maxDepth - depth + minDepth);
            check = 0;

            for (int i = 1; i <= iterations; i++) {
                check += (TreeNode.create(i, depth)).check();
                check += (TreeNode.create(-i, depth)).check();
            }
            //System.out.println((iterations << 1) + "\t trees of depth " + depth + "\t check: " + check);
        }

        //System.out.println("long lived tree of depth " + maxDepth + "\t check: " + longLivedTree.check());
    }

    static class TreeNode {
        int item;
        TreeNode left;
        TreeNode right;

        static TreeNode create(int item, int depth) {
            return childTreeNodes(item, depth - 1);
        }

        static TreeNode childTreeNodes(int item, int depth) {
            TreeNode node = new TreeNode(item);
            if (depth > 0) {
                node.left = childTreeNodes(2 * item - 1, depth - 1);
                node.right = childTreeNodes(2 * item, depth - 1);
            }
            return node;
        }

        TreeNode(int item) {
            this.item = item;
        }

        int check() {
            return left == null ? item : left.check() - right.check() + item;
        }
    }
}
