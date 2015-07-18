

ClutchGUI = {
    triggerFormFragment = function(index) {
        var ret = Document.createElement("div");
        var prefix = function(suff) {return "trg"+index+"_"+suff;};
        ret.appendChild(this.labelTxtInp(this.mkTxtInp(prefix("name"), null), "Name"));
        var d = Document.createElement("div");
        d.appendChild(Document.createTextNode("Say the phrase "));
        d.appendChild(this.mkTxtInp(prefix("phrase"), null));
        d.appendChild(Document.createTextNode(" when the number of the following triggers in a message is "));
        d.appendChild(this.mkOpSel(prefix("op_sel")));
        d.appendChild(this.mkNumInp(prefix("number")));
        var tr_prefix = function(i) {prefix("_tr_" + i);};
        var ul = Document.createElement("ul");
        this.more(5, 0, tr_prefix, ul);

    }

    mkOpSel = function(name) {
        var ret = Document.createElement("select");
        ret.setAttribute("name", name);
        ret.innerHTML = '<option value=">">more than</option><option value="=">equal to</option><option value="<">less than</option>';
        return ret;
    }

    // n = number more to add each time
    // i = starting index (inclusive)
    // tr_prefix = fn maps from [index] to form name for trigger phrase with [index]
    // parent = <ul> or <ol> item to insert <li> items into
    more = function(n, i, tr_prefix, parent) {
        for (var j=i; j < i+n-1; j++) {
            var li = Document.createElement("li");
            li.appendChild(this.mkTxtInp(tr_prefix(j), null));
            parent.appendChild(li);
        }
        var final_li = Document.createElement("li");
        var inp = this.mkTxtInp(tr_prefix(i+n-1));
        inp.addEventListener("change", function(e) {
            more(n, i+n, tr_prefix, parent); e.target.removeEventListener(e.type, arguments.callee);
        })
        final_li.appendChild(inp);
        parent.appendChild(final_li);
    }

    labelTxtInp(txtInp, label) {
        ret.appendChild(this.mkLabel(txtInp.getAttribute("id"), label));
        ret.appendChild(txtInp);
        return ret;
    }

    mkNumInp = function(name) {
        return this.mkInp(name, "number" null);
    }

    mkTxtInp = function(name, def) {
        return this.mkInp(name, "text", def);
    }

    mkInp = function(name, type, def) {
        var id = name + "_inp";
        var input = Document.createElement("input");
        input.setAttribute("type", type);
        input.setAttribute("id", id);
        input.setAttribute("name", name);
        if (def != null) input.setAttribute("value", def);
        return input;
    }

    mkLabel = function(for_id, label) {
        var l = Document.createElement("label");
        l.innerHTML = label;
        l.setAttribute("for", id);
        return l;
    }
}
