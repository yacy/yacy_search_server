      <style type="text/css"><!--
        .hostPort { 
          color: green;
          font-weight: bold;
        }
      --></style>
      
      <p>The server <b>#[hostName]#</b> could not be found.</p>
      #(showList)#::
      <p>Did you mean:</p>
      #(/showList)#
      <ul class="tagList">#{list}# 
        <li><a class="hostPort" href="http://#[hostName]#:#[hostPort]##[hostPath]##[hostArgs]#">#[hostName]#</a></li>#{/list}# 
      </ul>
