package nodes;

public class SpaceNode extends ExpressionNode{
    public SpaceNode() {}

    @Override
    public NodeType getNodeType() {
        return NodeType.SPACE;
    }

    @Override
    public Object getValue() {
        return " ";
    }
}
