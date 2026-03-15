package net.momirealms.craftengine.core.plugin.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class TranslationConfigConstructor extends SafeConstructor {

    public TranslationConfigConstructor(LoaderOptions loaderOptions) {
        super(loaderOptions);
    }

    @Override
    protected Map<Object, Object> constructMapping(MappingNode node) {
        Map<Object, Object> map = new LinkedHashMap<>();
        for (NodeTuple tuple : node.getValue()) {
            Node keyNode = tuple.getKeyNode();
            Node valueNode = tuple.getValueNode();
            String key = constructScalar((ScalarNode) keyNode);
            Object value = constructObject(valueNode);
            if (value instanceof List<?> list) {
                StringJoiner stringJoiner = new StringJoiner("<reset><newline>");
                for (Object str : list) {
                    stringJoiner.add(String.valueOf(str));
                }
                map.put(key, stringJoiner.toString());
            } else {
                map.put(key, value.toString());
            }
        }
        return map;
    }
}
