var PageShape={contextStack:null,paths:null,indexStack:null,lengthStack:null,templates:{},functions:{},addTemplate:function(a,c){this.templates[a]=c},addFunction:function(a,c){this.functions[a]=c},render:function(a,c){var b=this.templates[a];if(!b)return console.log("PageShape: Error: Attempt to call undefined template "+a),"";this.paths=b.paths;this.contextStack=[];this.indexStack=[];this.lengthStack=[];return this.renderer(b.template,c,0,b.template.length)},renderer:function(a,c,b,f){var d="";for(this.contextStack.push(c);b<
f;b++)switch(a[b][0]){case "P":d+=a[b][1];break;case "V":var e=this.selectContext(a[b][1]),d=d+(void 0!=e?e:"");break;case "L":if((e=this.selectContext(a[b][1]))&&e.length){var g=e.length;this.lengthStack.push(e.length);for(var h=0;h<g;h++)this.indexStack.push(h),d+=this.renderer(a,e[h],b+1,parseInt(a[b][2])),this.indexStack.pop();this.lengthStack.pop()}else d+=this.renderer(a,c,parseInt(a[b][2]),parseInt(a[b][3]));b=parseInt(a[b][3])-1;break;case "I":!1==this.testCondition(a[b])&&(b=parseInt(a[b][4])-
1);break;case "J":b=parseInt(a[b][1])-1;break;case "W":e=this.selectContext(a[b][1]);g=parseInt(a[b][2]);e&&(d+=this.renderer(a,e,b+1,g));b=g-1;break;case "F":e=this.selectContext(a[b][2]),g=this.functions[a[b][1]],e&&g.call&&(d+=g.call(this,e))}this.contextStack.pop();return d},testCondition:function(a){var c=this.selectContext(a[2]);switch(a[1]){case "0":if(!c||!1==c||""==c||null==c)break;return!0;case "1":return c<this.selectContext(a[3]);case "2":return c>this.selectContext(a[3]);case "3":return c<=
this.selectContext(a[3]);case "4":return c>=this.selectContext(a[3]);case "5":return c==this.selectContext(a[3]);case "6":return c!=this.selectContext(a[3])}return!1},selectContext:function(a){a=this.paths[a];switch(a[0]){case "V":for(var c=this.contextStack[this.contextStack.length-(parseInt(a[1])+1)],b=a.length,f=2;f<b&&"$"!=a[f];f++){if("^"==a[f])return this.indexStack[this.indexStack.length-(parseInt(a[1])+1)]+1;if("*"==a[f])return this.lengthStack[this.lengthStack.length-(parseInt(a[1])+1)];
c=c[a[f]];if(null==c||void 0==c){console.log("PageShape: Error: Attempt to call undefined variable "+a[f]);return}}return c;case "S":return a[1];case "N":return parseFloat(a[1]);case "B":return"true"==a[1]?!0:!1}}};