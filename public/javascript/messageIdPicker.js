(function ( jQ ) {

    jQ.fn.messageIdPicker = function(options, datasets, onSelect) {
        return this.each(function(index, elem) {
            var inputGroup = jQ(elem).find(".input-group");
            var messageIdent = jQ(elem).find(".message-ident");

            var input = inputGroup.find(".typeahead");
            var messageIdentContent = messageIdent.find(".message-ident-content");
            var messageIdentDelete = messageIdent.find(".close");
            var suggestionTemplate = datasets.templates.suggestion;

            messageIdent.click(function () {
                inputGroup.show();
                messageIdent.hide();
            });
            messageIdentDelete.click(function () {
                input.val("");
            });

            input.bind('typeahead:render', function () {
                inputGroup.find("time.timeago").timeago();
            });
            var showIdent = function (suggestion) {
                messageIdentContent.html(suggestionTemplate(suggestion));
                messageIdentContent.find("time.timeago").timeago();
                inputGroup.hide();
                messageIdent.show();
                onSelect(suggestion);
            };
            input.bind('typeahead:select', function (ev, suggestion) { showIdent(suggestion) });

            var initialMessageId = input.val();
            if (initialMessageId !== "") {
                function showIdentIfSingleResult(datums) {
                    if (datums.length == 1) { showIdent(datums[0]); }
                }
                datasets.source(initialMessageId, showIdentIfSingleResult, showIdentIfSingleResult);
            }

            input.typeahead(options, datasets);
        });
    };

}( jQuery ));





