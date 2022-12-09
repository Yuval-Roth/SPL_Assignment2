package bguspl.set.ex;

public class AISuperSecretIntelligenceService{

    Tree<Integer> tree;

    public AISuperSecretIntelligenceService(){
        int cardsCount = 12;
        tree = new Tree<Integer>(cardsCount);
        Tree<Integer>.TreeNode root = tree.getRoot();
        for(int i = 0; i < cardsCount; i++){
            root.insertNewChildAt(i, null, cardsCount);
            for(int j = 0 ; i< cardsCount-1;j++){
                root.children[i].insertNewChildAt(j, null, cardsCount);
                for(int k = 0 ; i< cardsCount-2;j++){
                    root.children[i].children[j].insertNewChildAt(k, null, 1);
                    root.children[i].children[j].children[0].insertNewChildAt(0, 0, 0);
                }
            }
        }
    }
}