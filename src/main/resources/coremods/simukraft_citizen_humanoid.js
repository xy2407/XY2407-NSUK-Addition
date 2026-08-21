/**
 * 把 simukraft 市民模型从 PlayerModel 改为 HumanoidModel，规避 Photon 光影下的玩家实体透明问题。
 */
function initializeCoreMod() {
    var Opcodes = Java.type('org.objectweb.asm.Opcodes');
    var METHOD_INSN = 5;
    var FIELD_INSN = 4;
    var PlayerModel = 'net/minecraft/client/model/PlayerModel';
    var HumanoidModel = 'net/minecraft/client/model/HumanoidModel';
    var DESC_PLAYER = 'Lnet/minecraft/client/model/PlayerModel;';
    var DESC_HUMANOID = 'Lnet/minecraft/client/model/HumanoidModel;';
    var MODEL_PART = 'Lnet/minecraft/client/model/geom/ModelPart;';

    function replacePlayerModelInDesc(desc) {
        var s = String(desc);
        if (s.indexOf(DESC_PLAYER) < 0) return s;
        return s.split(DESC_PLAYER).join(DESC_HUMANOID);
    }

    function transformCitizenModel(classNode) {
        if (String(classNode.superName) === PlayerModel) classNode.superName = HumanoidModel;
        var methods = classNode.methods;
        for (var m = 0; m < methods.size(); m++) {
            var method = methods.get(m);
            var insns = method.instructions;
            for (var i = 0; i < insns.size(); i++) {
                var insn = insns.get(i);
                if (insn.getType() !== METHOD_INSN) continue;
                if (String(insn.owner) === PlayerModel) {
                    if (String(insn.name) === '<init>') {
                        var prev = insn.getPrevious();
                        if (prev !== null && prev.getOpcode() === Opcodes.ILOAD) insns.remove(prev);
                        insn.desc = '(' + MODEL_PART + ')V';
                    }
                    insn.owner = HumanoidModel;
                }
                insn.desc = replacePlayerModelInDesc(insn.desc);
            }
        }
        return classNode;
    }

    function transformCitizenAnimationActions(classNode) {
        var methods = classNode.methods;
        for (var m = 0; m < methods.size(); m++) {
            var method = methods.get(m);
            var insns = method.instructions;
            method.desc = replacePlayerModelInDesc(method.desc);
            var removed = true;
            while (removed) {
                removed = false;
                for (var i = 0; i < insns.size(); i++) {
                    var insn = insns.get(i);
                    if (insn.getType() === METHOD_INSN) {
                        insn.desc = replacePlayerModelInDesc(insn.desc);
                        continue;
                    }
                    if (insn.getType() !== FIELD_INSN || String(insn.owner) !== PlayerModel) continue;
                    if (String(insn.name) === 'rightSleeve' || String(insn.name) === 'leftSleeve') {
                        var a = insn.getPrevious();
                        var c = insn.getNext();
                        var d = c.getNext();
                        var e = d.getNext();
                        insns.remove(a); insns.remove(insn); insns.remove(c); insns.remove(d); insns.remove(e);
                        removed = true;
                        break;
                    }
                    insn.owner = HumanoidModel;
                }
            }
        }
        return classNode;
    }

    return {
        'simukraft_citizen_model_humanoid': {
            'target': { 'type': 'CLASS', 'name': 'client.cn.kafei.simukraft.client.renderer.CitizenModel' },
            'transformer': transformCitizenModel
        },
        'simukraft_citizen_animation_actions': {
            'target': { 'type': 'CLASS', 'name': 'client.cn.kafei.simukraft.client.renderer.CitizenAnimationActions' },
            'transformer': transformCitizenAnimationActions
        }
    };
}
