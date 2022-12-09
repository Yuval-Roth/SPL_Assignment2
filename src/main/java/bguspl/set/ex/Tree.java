package bguspl.set.ex;


public class Tree<T>{

    private TreeNode root;

    public Tree(int nodeSize){
        root = new TreeNode(null,null,nodeSize);
    }

    public TreeNode getRoot(){
        return root;
    }

    public class TreeNode{
        public TreeNode[] children;
        public TreeNode parent;
        public T value;

        public TreeNode(T value, TreeNode parent,int nodeSize){
            this.value = value;
            if(nodeSize > 0) children = (TreeNode[])new Object[nodeSize];
            this.parent = parent;
        }

        public int getNodeSize(){
            return children.length;
        } 

        public TreeNode insertNewChildAt(int i, T value,int nodeSize){
            if(children[i] == null) children[i] = new TreeNode(value,this,nodeSize);
            else throw new RuntimeException("A node already exists at index "+i);

            return children[i];
        }

    }
}