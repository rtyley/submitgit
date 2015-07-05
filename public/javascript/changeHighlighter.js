(function ( jQ ) {
    jQ.fn.changeVal = function(newVal) {
        var highlighterClass = "highlighter";

        return this.each(function(index, elem) {
            var jqElem = jQ(elem);

            if (newVal && jqElem.val() != newVal) {
                jqElem.val(newVal);
                jqElem.bind('animationend', function(){ jqElem.removeClass(highlighterClass); });
                jqElem.addClass(highlighterClass);
            }
        });
    };

}( jQuery ));
