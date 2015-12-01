package start;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.common.base.Predicate;
import static com.google.common.base.Predicates.or;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2 // Loads the spring beans required by the framework
public class SwaggerConfig {

	/**
	 * Every Docket bean is picked up by the swagger-mvc framework - allowing
	 * for multiple swagger groups i.e. same code base multiple swagger resource
	 * listings.
	 */
	@Bean
	public Docket customDocket() {
		return new Docket(DocumentationType.SWAGGER_2).apiInfo(apiInfo()).select().paths(paths()).build();
	}

	private ApiInfo apiInfo() {
		return new ApiInfoBuilder().title("FIM BA").description("API der einzelnen Endpunkte.")
				.contact("Tobias Ruby (tobias.ruby@fim-rc.de)").version("1.0.0").build();
	}

	private Predicate<String> paths() {
		return or(PathSelectors.regex("/consumers.*"), PathSelectors.regex("/devices.*"),
				PathSelectors.regex("/marketplace.*"));
	}

}